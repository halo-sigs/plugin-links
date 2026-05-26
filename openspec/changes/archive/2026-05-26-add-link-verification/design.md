## Context

The plugin already stores desired link data in `Link.spec` and observed refresh state in
`Link.status.rss`. The Console lists `Link` objects through generated APIs and renders each item
with `LinkBadge`. Remote URL fetching already has an SSRF-safe path used by link detail and RSS
features.

Verification has a different lifecycle from RSS refresh. It must answer an operator question
("is this friend link still reachable and linking back?") without turning plugin startup or
ordinary background scheduling into a burst of external HTTP requests.

## Goals / Non-Goals

**Goals:**

- Store optional backlink scan configuration on each `Link`.
- Store reachability and backlink verification results in `Link.status`.
- Let Console users trigger verification for all links, one group, or a selected set of links.
- Trigger one non-blocking verification run after Console create/edit succeeds.
- Display reachability and backlink results on `LinkBadge`.
- Keep verification requests bounded, SSRF-protected, and isolated per link.

**Non-Goals:**

- No reconciler-driven verification.
- No startup scan of existing links.
- No scheduled recurring verification.
- No headless browser rendering for JavaScript-only backlink pages.
- No persistent job queue or historical verification log in the first implementation.

## Decisions

### Decision 1: Explicit trigger API with in-memory asynchronous execution

Add a Console endpoint such as `POST /links/-/verification/check` that accepts an optional list of
link names or a group name. The endpoint resolves the requested links, enqueues each link for
background verification, and returns `202 Accepted` with counts for accepted, skipped, and already
running links.

Rationale: this centralizes bulk scope resolution and concurrency control in the backend while
keeping the user-facing operation responsive. An in-memory queue is enough because verification is
advisory status; losing queued work on restart is acceptable and avoids a new persistence surface.

Alternative considered: call a single-link endpoint repeatedly from the frontend. That keeps the
backend smaller but duplicates batching, retry, and concurrency behavior in Console code.

### Decision 2: No reconciler, no startup scan, no scheduler

Verification runs only when requested by Console actions or after Console create/edit success.

Rationale: reconciler-based checks would run for existing links on startup if `syncAllOnStart` is
used, and status updates can also feed back into reconcile loops unless carefully guarded. This
feature is best treated as an explicit operator action.

Alternative considered: use `LinkReconciler` with a spec hash. That would cover every write path,
but it violates the no-startup-scan constraint and creates more control-plane complexity than the
feature needs.

### Decision 3: Add a dedicated `spec.verification` and `status.verification`

`spec.verification.backlinkScanUrl` holds the optional fixed page to scan for a reciprocal link.
`status.verification` holds the latest reachability and backlink result, including checked time,
state, HTTP status/final URL where useful, target URL, matched URL, and error message.

Rationale: this follows the existing `spec.rss` / `status.rss` shape without mixing unrelated
health data into RSS. Existing links can omit the new spec/status fields and display as unknown.

### Decision 4: Backlink matching uses Halo external URL and normalized anchors

The target URL should come from Halo's configured external URL (`ExternalUrlSupplier.getRaw()`).
When the scan page is fetched, the service parses HTML anchors and normalizes URLs before matching.
A link is a backlink when it points to the configured site origin, including the configured base
path when Halo is deployed under a subpath.

Rationale: site owners should configure where to scan on the remote site, but they should not need
to repeat their own site URL per friend link. Anchor-based matching keeps the first version simple
and testable.

Alternative considered: scan raw HTML text or render JavaScript. Raw text produces false positives,
while browser rendering is much heavier and should wait for real demand.

### Decision 5: Save/edit checks are fire-and-forget from Console

After create/edit succeeds, Console triggers verification for the saved link name without blocking
the modal close or success toast. The list invalidates and short-polls while any visible link has a
verification state of `CHECKING`.

Rationale: remote sites can be slow or temporarily unavailable. Save/edit should remain about
persisting the Link; verification status can arrive shortly afterward.

## Risks / Trade-offs

- [Risk] Manual "check all" can produce many external requests. -> Mitigation: limit backend
  concurrency, de-duplicate already running link names, and record per-link failures instead of
  failing the whole batch.
- [Risk] Sites that require JavaScript to render backlinks may be reported as missing. ->
  Mitigation: document the scan as HTML anchor based and keep the configured scan URL visible.
- [Risk] Halo external URL may be missing. -> Mitigation: mark backlink verification as failed or
  unknown with a clear message while still running reachability checks.
- [Risk] Verification status can be stale after later remote-site changes. -> Mitigation: Badge
  tooltips include the last checked time and users can run manual checks again.
- [Risk] The existing safe fetcher reads response bodies. -> Mitigation: use or add a lightweight
  bounded fetch mode for reachability and a smaller bounded HTML mode for backlink scans.

## Migration Plan

Existing `Link` resources need no data migration. Their missing verification spec means backlink
checks are not configured, and missing verification status is displayed as unknown until a user
triggers a check.

Rollback is straightforward: removing the new endpoint and UI leaves extra `spec.verification` and
`status.verification` fields ignored by older code.
