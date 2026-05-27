## Context

Link verification already stores reachability and reciprocal backlink results under
`Link.status.verification`. Manual Console triggers and create/edit follow-up checks use
`DefaultLinkVerificationService`, which runs verification asynchronously with bounded per-link
deduplication and SSRF-protected fetches.

The plugin also already enables Spring scheduling for RSS refresh and database maintenance. This
change should add scheduled link verification as a small orchestration layer instead of creating a
second verification implementation.

## Goals / Non-Goals

**Goals:**

- Add plugin-level settings for automatic verification: enabled flag, interval, per-run limit, and
  whether automatic runs include backlink checks.
- Keep automatic verification disabled by default.
- Reuse the existing verification service, queueing, failure isolation, and SSRF protections.
- Prevent access-only automatic runs from overwriting existing backlink status.
- Keep scheduled external requests bounded and predictable.

**Non-Goals:**

- No historical verification log or persistent job queue.
- No notification system for failed links.
- No cron-expression UI in the first version.
- No change to the behavior of manual Console verification triggers.
- No new per-Link automatic verification settings.

## Decisions

### Decision 1: Store automation controls in plugin settings

Add a `verification` settings group with these fields:

- `enabled`: default `false`
- `intervalHours`: default `24`
- `maxLinksPerRun`: default `50`
- `checkBacklink`: default `false`

Rationale: `spec.verification.backlinkScanUrl` already describes how one link should be checked.
Scheduling policy is plugin-wide operational behavior, so it belongs in plugin settings rather
than every `Link` resource.

Alternative considered: add automation flags to each `Link`. That would support per-link cadence,
but it increases model/API churn and makes the first version harder to reason about.

### Decision 2: Use a polling scheduler with dynamic setting reads

Add a scheduler that wakes on a fixed internal cadence, reads `ReactiveSettingFetcher`, and starts
work only when the setting is enabled and the configured interval has elapsed. Track the latest
automatic run timestamp in memory and initialize it at plugin startup so startup does not cause an
immediate all-link scan.

Rationale: Spring `@Scheduled` annotations are static, while plugin settings are dynamic. A small
polling scheduler keeps settings live without programmatic scheduler management.

Alternative considered: reschedule tasks whenever plugin settings change. That is more precise but
adds lifecycle complexity that is not needed for an hourly-or-daily maintenance task.

### Decision 3: Select a bounded batch per scheduled run

Automatic runs should list links, prioritize links that have never been checked or were checked
least recently, and enqueue at most `maxLinksPerRun`. Selection can sort in memory for the first
version because scheduled runs are already infrequent and external network requests are the
dominant cost.

Rationale: The per-run cap protects the server and remote sites even when a site owns many links.
Oldest-first selection lets large link sets converge across multiple runs.

Alternative considered: add an index for `status.verification.lastCheckedAt`. That can be added
later if real link counts make in-memory ordering too expensive.

### Decision 4: Add an internal verification mode

Extend the verification service internally so callers can choose whether to include reciprocal
backlink checks. Manual Console triggers keep using the current full verification mode. The
automatic scheduler uses access-only mode unless `checkBacklink` is enabled.

When backlink checks are not included, verification should update reachability status and preserve
the existing backlink status. The intermediate `CHECKING` status and unexpected failure path should
also avoid replacing backlink status for access-only runs.

Rationale: Backlink checks can double the number of external requests and may fetch larger HTML
pages. Site owners should opt into that cost separately.

Alternative considered: extend the public Console verification request DTO with a backlink flag.
That would require generated client updates and expose an option that is only needed by the
internal scheduler.

## Risks / Trade-offs

- Scheduled checks can still create external request load when enabled -> keep the feature opt-in,
  enforce interval and per-run limits, and reuse the service's single-link concurrency.
- In-memory last-run state resets on plugin restart -> initialize the scheduler as not due at
  startup so restarts do not trigger scans; users can still run manual checks immediately.
- Access-only automatic runs may leave old backlink status visible -> preserve timestamps on the
  backlink sub-status so the UI can show that the backlink result is older than reachability.
- Listing all links for batch selection may become expensive on very large sites -> keep the first
  version simple and add an indexed selector later only if needed.

## Migration Plan

Existing installations start with automatic verification disabled. Adding the settings group does
not modify existing `Link` resources or existing verification status. Enabling the setting starts
future scheduled runs only after the configured interval gate allows a run.

Rollback is straightforward: disabling the setting stops scheduled verification and preserves the
last recorded statuses.

## Open Questions

None for the first version.
