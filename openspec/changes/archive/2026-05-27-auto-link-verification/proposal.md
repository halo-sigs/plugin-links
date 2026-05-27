## Why

Link verification currently depends on Console-triggered checks, so stale or broken friend links
can go unnoticed after the initial save flow. Site owners need an opt-in scheduled verification
mode that keeps reachability status fresh without forcing manual all-link checks.

## What Changes

- Add a plugin settings group for link verification automation.
- Let administrators enable or disable scheduled automatic link verification.
- Let administrators configure the automatic verification interval and the maximum number of
  links checked in one scheduled run.
- Add an option to include reciprocal backlink checks during automatic verification.
- Keep manual verification behavior unchanged: explicit Console checks still verify reachability
  and reciprocal backlinks according to each link's existing verification settings.
- Keep automatic verification disabled by default and bounded when enabled.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `link-verification`: Add opt-in scheduled verification settings and automatic verification
  behavior, including whether scheduled runs also check reciprocal backlinks.

## Impact

- Backend: plugin setting model, scheduled verification orchestration, verification service mode
  support, and tests.
- Frontend/Console: plugin settings schema in `settings.yaml`.
- OpenAPI/API client: no public Console endpoint is expected to change unless implementation
  chooses to expose the new verification mode through generated DTOs.
- Operations: scheduled verification may perform external HTTP(S) requests only when enabled by
  the administrator and must remain bounded by interval and per-run limits.
