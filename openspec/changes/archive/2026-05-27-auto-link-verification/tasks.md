## 1. Settings

- [x] 1.1 Add a `verification` group to `src/main/resources/extensions/settings.yaml` with
  `enabled`, `intervalHours`, `maxLinksPerRun`, and `checkBacklink` fields.
- [x] 1.2 Add a backend automatic verification settings model/loader using
  `ReactiveSettingFetcher`, including disabled-by-default behavior and safe defaults for missing
  or invalid values.

## 2. Verification Service

- [x] 2.1 Add an internal verification option or mode that lets callers include or skip reciprocal
  backlink checks without changing the public Console verification request DTO.
- [x] 2.2 Keep existing manual verification and create/edit follow-up checks on the full
  reachability-plus-backlink mode.
- [x] 2.3 Ensure access-only verification updates reachability status while preserving the
  existing backlink status during checking, success, and unexpected-failure paths.

## 3. Scheduler

- [x] 3.1 Add a scheduled automatic verification component that reads plugin settings dynamically
  and does nothing while automatic verification is disabled.
- [x] 3.2 Gate automatic runs by the configured interval and avoid an immediate startup scan.
- [x] 3.3 Select links that have never been checked or were checked least recently, cap each run by
  `maxLinksPerRun`, and enqueue them through the existing verification service safeguards.
- [x] 3.4 Pass the scheduled run's backlink mode from the `checkBacklink` setting.

## 4. Tests and Validation

- [x] 4.1 Add backend tests for settings defaults, disabled scheduler behavior, interval gating,
  batch limiting, stale-link selection, and backlink-mode propagation.
- [x] 4.2 Add verification service tests proving access-only mode preserves existing backlink
  status while manual/full mode still checks backlinks.
- [x] 4.3 Run targeted backend tests for link verification and scheduler behavior.
- [x] 4.4 Run `./gradlew test`.
- [x] 4.5 Run `openspec validate auto-link-verification --strict`.
- [x] 4.6 Run `git diff --check`.
