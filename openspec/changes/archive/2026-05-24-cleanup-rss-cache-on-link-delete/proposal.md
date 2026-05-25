## Why

Deleting a `Link` currently removes the Halo Extension resource but leaves its cached RSS/Atom items in the plugin-local feed store. Those orphaned items can still appear in the RSS updates "all updates" view and keep consuming cache space after the source link no longer exists.

## What Changes

- Add lifecycle behavior so deleting a `Link` also removes RSS/Atom feed items cached for that link.
- Use the `Link` reconciliation lifecycle to perform cleanup before the `Link` resource is finalized.
- Keep saved feed item state protection for normal retention cleanup only; explicit link deletion removes all cached items for that link, including read, favorite, and read-later items.
- Keep existing RSS refresh, discovery, listing, and retention APIs unchanged.

## Capabilities

### New Capabilities

### Modified Capabilities

- `link-rss-feed`: cached feed items associated with a deleted `Link` are removed during link deletion.

## Impact

- Backend: add a `Link` reconciler/finalizer path and a feed item store operation for deleting cached items by link name.
- Backend tests: cover store-level deletion by link name and reconciler cleanup/finalizer behavior.
- APIs/frontend: no request or response contract changes expected.
