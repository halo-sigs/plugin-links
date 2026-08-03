## Why

The RSS updates header exposes separate favorite, read-later, and hidden-item workflows, but only the hidden workflow shows how many items are available. Users cannot tell whether the other saved-item entries contain anything without opening them, and the current hidden-only count API does not match the unified header use case.

## What Changes

- Add one Console RSS item summary contract returning exact `hiddenCount`, `favoriteCount`, and `readLaterCount` values.
- Display all three counts on the existing header actions, including resolved zero values and `(0)` while the summary is loading to keep the layout stable.
- Count hidden items globally; count favorite and read-later items globally while excluding hidden rows from those saved-item counts.
- Refresh the summary after any state change that can affect a counted collection, including save-state changes, hide/unhide, opening read-later items, and link deletion.
- **BREAKING** Remove the existing `GET .../rss/items/-/hidden-count` endpoint and replace its frontend, generated-client, RBAC, documentation, and test references with the unified summary contract.
- Keep the existing unread summary API and per-link unread behavior separate.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `link-rss-feed`: extend the Console saved-item and hidden-item workflows with unified header counts, saved-count visibility semantics, summary refresh behavior, and the replacement Console API contract.

## Impact

- Backend RSS item store, summary DTO, and `LinkFeedEndpoint` routes.
- Console Vue header, query composable/cache invalidation, and item-action mutation paths.
- Generated TypeScript API client, OpenAPI artifacts, developer REST documentation, and link RBAC role template.
- Backend, frontend, endpoint, storage, security, and view/composable tests.
