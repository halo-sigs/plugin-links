## 1. Settings

- [x] 1.1 Add an RSS automatic refresh settings group to `settings.yaml` with `enabled`,
  `intervalHours`, and `maxLinksPerRun` fields, defaulting `enabled` to `true`.
- [x] 1.2 Add a backend RSS refresh settings model/loader using plugin settings, including
  default-enabled behavior and safe defaults for missing or invalid values.

## 2. Scheduler

- [x] 2.1 Update `LinkFeedScheduler` to read RSS refresh settings dynamically and skip automatic
  refresh while globally disabled.
- [x] 2.2 Gate scheduled RSS refreshes by the configured interval without running an immediate
  startup refresh.
- [x] 2.3 Select eligible RSS-enabled links with feed URLs by missing or oldest
  `status.rss.lastFetchedAt`, and cap each run by `maxLinksPerRun`.
- [x] 2.4 Preserve existing manual RSS refresh behavior and per-link `spec.rss.enabled` semantics.

## 3. Tests and Validation

- [x] 3.1 Add backend tests for settings defaults, disabled scheduler behavior, interval gating,
  batch limiting, stale-link selection, and default-enabled scheduling.
- [x] 3.2 Add regression coverage proving disabled global scheduling does not mutate link-level RSS
  settings or block manual refresh.
- [x] 3.3 Run targeted backend tests for RSS scheduler behavior.
- [x] 3.4 Run `./gradlew test`.
- [x] 3.5 Run `openspec validate configure-rss-auto-refresh --strict`.
- [x] 3.6 Run `git diff --check`.
