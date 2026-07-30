## Why

Friend-link application defenses currently limit individual visitor behavior but do not bound the
total pending-review backlog shared by visitor submissions and Comment recognition. A configurable
hard capacity prevents storage and review queues from growing without limit when applications are
submitted through many URLs or sources.

## What Changes

- Add a Security subgroup to friend-link application settings with a required positive
  `pendingCapacity` value that defaults to `100`.
- Count only `PENDING` LinkApplications toward one capacity shared by visitor forms and Comment
  recognition.
- Enforce the capacity as a hard upper bound across supported creation paths within one plugin
  instance, including concurrent submissions for different canonical URLs.
- Preserve existing applications when the configured capacity is lowered and reject new
  applications until the pending count falls below the configured value.
- Return a stable visitor redirect when capacity is exhausted, while preserving the existing
  CAPTCHA, rate-limit, validation, and duplicate-result ordering.
- Skip Comment AI recognition when a capacity precheck is already full, repeat the authoritative
  capacity check before persistence, and do not retry or backfill Comments skipped for capacity.
- Fail closed when capacity configuration or the authoritative pending-count query is unavailable.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `link-application`: Add the pending-capacity setting, shared capacity semantics, full-capacity
  visitor result, and cross-URL single-instance concurrency guarantee.
- `comment-link-application-recognition`: Define capacity-aware model invocation, creation, and
  no-retry behavior.

## Impact

- Backend: application settings DTO/fetching, shared LinkApplication creation coordination and
  results, visitor form routing, Comment recognition, and focused concurrency/error handling.
- Configuration: `application.security.pendingCapacity` in the plugin settings schema.
- Theme contract: one additional `/links/apply` error redirect; form availability and CAPTCHA
  endpoints remain unchanged while capacity is full.
- Documentation and tests: update the theme API and add settings, service concurrency, route, and
  Comment-recognition coverage.
- No generated Console API change, data migration, legacy-setting compatibility, new dependency,
  distributed quota, or direct Extension API admission control.
