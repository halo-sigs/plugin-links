## Why

RSS subscriptions currently refresh automatically on a fixed schedule, but administrators cannot
turn that background work off or tune its cadence. Sites need the same kind of operational control
as automatic link verification while preserving the current default behavior.

## What Changes

- Add plugin settings for scheduled RSS refresh automation.
- Keep scheduled RSS refresh enabled by default for backward-compatible behavior.
- Let administrators disable scheduled RSS refresh.
- Let administrators configure the refresh interval and the maximum number of links refreshed in
  one scheduled run.
- Keep manual RSS refresh behavior unchanged.
- Keep link-level `spec.rss.enabled` as the per-link subscription switch.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `link-rss-feed`: Add configurable scheduled RSS refresh behavior, including default-enabled
  plugin settings, disable support, interval control, and per-run limits.

## Impact

- Backend: plugin setting model/loader and `LinkFeedScheduler` orchestration.
- Frontend/Console: plugin settings schema in `settings.yaml`.
- OpenAPI/API client: no generated API changes expected.
- Operations: scheduled RSS refresh remains enabled by default, but can be disabled or bounded by
  administrators.
