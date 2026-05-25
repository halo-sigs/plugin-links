## Context

The RSS feature currently models each `Link` as having at most one RSS/Atom
source through `spec.rss.feedUrl`. Runtime state mirrors that single-source
shape with one set of conditional request validators on `status.rss`.

That shape is too narrow for friend links. A single website can expose several
useful feeds while still being one friend link in the Links plugin. The feature
has not shipped yet, so this change can replace the single-source contract
directly instead of supporting both `feedUrl` and `feedUrls`.

## Goals / Non-Goals

**Goals:**

- Replace the unreleased single-feed configuration with `spec.rss.feedUrls`.
- Keep RSS enablement, subscription listing, item browsing, and filtering
  centered on `Link`.
- Refresh every configured feed URL for an enabled Link, with per-feed status and
  per-feed conditional request metadata.
- Keep feed item records associated with the owning Link while retaining the
  source `feedUrl` for tracing and refresh accounting.
- Update Console forms to manage URL strings with a FormKit `code` input where
  each non-blank line becomes one feed URL.
- Regenerate OpenAPI docs and the Console API client after the model changes.

**Non-Goals:**

- No separate Feed or Subscription Extension.
- No per-feed enable/disable switch, display name, grouping, sorting, or sidebar
  identity.
- No compatibility layer for `spec.rss.feedUrl` or discovery result `feedUrl`.
- No public theme-facing RSS reader API in this change.

## Decisions

### 1. Use `feedUrls: string[]` instead of feed objects

`Link.RssSpec` will contain:

```text
spec.rss.enabled
spec.rss.feedUrls
```

The switch stays Link-level. A Link is either RSS-enabled or not; when enabled,
all non-blank configured feed URLs are considered part of that Link.

**Rationale**: The user intent is to attach multiple website feeds to one friend
link, not to manage a feed catalog. A string list matches the current product
surface and avoids premature per-feed options.

**Alternative considered**: `feeds: [{ url, enabled, displayName }]`. This was
rejected because it introduces per-feed identity and configuration the feature
does not need.

### 2. Treat `feedUrl` as removed, not deprecated

Backend models, OpenAPI schemas, generated TypeScript types, link forms, and
discovery results should move to plural naming only. Tests and specs should not
preserve fallback branches for single `feedUrl` data.

**Rationale**: The feature has not shipped. Keeping both names would add
long-lived ambiguity to every refresh and form path without protecting released
users.

### 3. Store aggregate Link status plus per-feed status entries

`status.rss` should keep aggregate fields useful for the Link list and
subscription sidebar, such as latest fetch/success times, latest published time,
total cached item count, and an aggregate or most recent error indicator.

It should also include per-feed status entries keyed by configured URL, holding
each feed's validators and failure state:

```text
status.rss.feeds[].url
status.rss.feeds[].lastFetchedAt
status.rss.feeds[].lastSuccessAt
status.rss.feeds[].lastError
status.rss.feeds[].failureCount
status.rss.feeds[].etag
status.rss.feeds[].lastModified
status.rss.feeds[].latestPublishedAt
status.rss.feeds[].itemCount
```

**Rationale**: ETag and Last-Modified are source-specific. Sharing validators
across feed URLs would repeat the empty-cache/304 class of bugs and make partial
failure states confusing.

### 4. Refresh all feed URLs with failure isolation

Manual and scheduled refresh should normalize the configured URL list, remove
blank and duplicate URLs, validate each URL with the existing RSS safety
boundary, and fetch each source independently. A failure from one feed URL should
update that feed's status while allowing the other feed URLs for the same Link
to continue.

The overall refresh response can aggregate counts and expose per-feed results so
the Console can report partial success without creating separate subscriptions.

**Rationale**: The Link remains the user-facing unit, but operational behavior
must stay feed-specific to avoid one bad source blocking the others.

### 5. Scope cached item identity by source feed URL

The deterministic cached item ID should include `linkName`, `feedUrl`, and the
entry identity. The item record already stores both `linkName` and `feedUrl`.

**Rationale**: RSS GUIDs are only reliable within a feed. Including the source
feed URL prevents different feeds under the same Link from overwriting each
other when they reuse GUIDs or weak identifiers.

**Trade-off**: If a user configures overlapping feeds, the same article can
appear more than once. That is acceptable for this change because the system is
preserving source-specific feed items rather than inventing cross-feed article
deduplication.

### 6. Keep Console browsing Link-based

The RSS updates view should continue to list subscribed Links, not feed URLs. A
Link with multiple feed URLs appears once in the sidebar. Selecting it filters
items by `linkName`, so items from all configured feed URLs under that Link are
shown together.

**Rationale**: This preserves the friend-link mental model and keeps the UI from
turning into a general-purpose RSS reader.

### 7. Return multiple feed discovery results

Feed discovery should collect all valid alternate RSS/Atom links from the target
website and return them as `feedUrls`. The Console can use this to populate or
append to the FormKit list, with duplicate suppression.

**Rationale**: Discovery is the natural entry point for websites that expose
multiple feeds. Returning only the first match would leave a single-feed tail in
the API contract.

## Risks / Trade-offs

- **[Risk] Partial success is harder to summarize at Link level** -> Keep
  aggregate status for quick UI badges and per-feed status for detail and
  debugging.
- **[Risk] Duplicate articles can appear when configured feeds overlap** -> Keep
  source-scoped item identity for correctness; defer cross-feed article
  deduplication until there is a clear product requirement.
- **[Risk] Refresh duration increases with multiple URLs per Link** -> Reuse the
  existing scheduler concurrency guard and keep per-Link feed refresh bounded by
  maximum items and fetch timeouts.
- **[Risk] Status shape becomes larger** -> Store only scalar per-feed runtime
  fields and never store item arrays, raw XML, article HTML, or summaries in
  `Link.status.rss`.
- **[Risk] Multiline form input can allow empty lines** -> Normalize and
  validate on both frontend and backend; enabled Links require at least one
  non-blank HTTP/HTTPS feed URL.

## Migration Plan

1. Replace backend Java models and OpenAPI schemas from `feedUrl` to `feedUrls`.
2. Update refresh/discovery/result models and tests for multiple feed URLs.
3. Update the embedded item identity and store helpers needed for per-feed
   counts and status.
4. Regenerate OpenAPI docs and the Console generated TypeScript client.
5. Update Console form state and FormKit `code` input parsing/rendering.
6. Validate with backend tests, UI type-check/lint, OpenSpec validation, and a
   Console smoke test that refreshes a Link with more than one feed URL.

Rollback during development is to revert the change and discard any unreleased
local feed cache/status data written with the plural model.

## Open Questions

- Should the Console discovery action append all discovered URLs automatically,
  or show a selectable list before adding them?
- Should the link edit surface expose per-feed failure details immediately, or
  keep the first implementation to aggregate badges and refresh toast messages?
