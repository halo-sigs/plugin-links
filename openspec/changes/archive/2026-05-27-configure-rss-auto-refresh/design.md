## Context

`LinkFeedScheduler` currently refreshes enabled RSS links every hour with a fixed initial delay.
The scheduler already filters by each link's `spec.rss.enabled` flag and skips enabled links that
do not have configured feed URLs. This gives users automatic RSS updates, but the global schedule
cannot be disabled or tuned from plugin settings.

The newly introduced link verification automation uses plugin settings for global background work.
RSS refresh should follow the same operational pattern while keeping its current default behavior:
scheduled RSS refresh remains on unless an administrator turns it off.

## Goals / Non-Goals

**Goals:**

- Add plugin-level settings for scheduled RSS refresh: enabled flag, interval, and per-run limit.
- Keep scheduled RSS refresh enabled by default.
- Let administrators disable scheduled RSS refresh without changing link-level RSS subscriptions.
- Keep manual RSS refresh behavior unchanged.
- Keep scheduled refresh bounded and dynamic without requiring a plugin restart after setting
  changes.

**Non-Goals:**

- No per-link RSS refresh cadence.
- No cron-expression UI in the first version.
- No change to feed discovery, feed parsing, retention, item listing, or manual refresh APIs.
- No new generated API client models expected.

## Decisions

### Decision 1: Add a dedicated RSS refresh settings group

Add a plugin settings group for RSS automatic refresh with these fields:

- `enabled`: default `true`
- `intervalHours`: default `1`
- `maxLinksPerRun`: default `50`

Rationale: `spec.rss.enabled` remains the per-link subscription switch. The new plugin setting
controls only background refresh orchestration for all subscribed links.

Alternative considered: putting schedule fields under each `Link.spec.rss`. That would allow
fine-grained cadences but would require extension/API model churn and a more complex scheduler.

### Decision 2: Reuse the existing scheduler and read settings dynamically

Keep `LinkFeedScheduler` as the scheduled component, but make it read plugin settings on each tick.
When disabled, it should return without listing links. When enabled and due, it should refresh a
bounded set of eligible links.

Rationale: the existing scheduler already owns RSS refresh orchestration and error isolation.
Reading settings dynamically mirrors the automatic link verification design and avoids static
`@Scheduled` values becoming user-facing configuration.

Alternative considered: programmatically register a scheduler with the configured interval. That
adds lifecycle complexity and does not add meaningful value for hour-scale refreshes.

### Decision 3: Bound each scheduled refresh run

Scheduled refresh should cap the number of links processed per run with `maxLinksPerRun`. Selection
should prefer links whose aggregate `status.rss.lastFetchedAt` is absent or oldest, so large sites
eventually refresh all subscribed links across multiple runs.

Rationale: RSS fetches are external network work. A per-run limit prevents one scheduled tick from
refreshing every subscribed link on large sites.

Alternative considered: keep refreshing all enabled links every tick. That preserves current
behavior exactly, but it leaves administrators without a load-control knob.

## Risks / Trade-offs

- Default-enabled background work still performs external requests -> keep current behavior for
  compatibility, but expose a global off switch and run cap.
- Large sites may take multiple intervals to refresh all subscriptions -> oldest-first selection
  makes progress fair across runs.
- In-memory interval tracking resets on restart -> initialize the scheduler so startup does not
  immediately refresh all feeds; manual refresh remains available.
- Settings fetch failure could accidentally stop updates -> fall back to defaults so scheduled RSS
  refresh remains enabled unless the administrator explicitly disables it.

## Migration Plan

Existing sites continue receiving scheduled RSS refreshes because the new setting defaults to
enabled with a one-hour interval. Administrators can later disable or tune the schedule from plugin
settings. No `Link` resources or cached feed items need migration.

## Open Questions

None for the first version.
