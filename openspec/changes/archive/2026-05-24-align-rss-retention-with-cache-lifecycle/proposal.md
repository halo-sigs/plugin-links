## Why

RSS retention currently treats an item's remote publication time as the cache age. That
causes valid but quiet feeds with old posts to refresh successfully and then immediately
drop all cached items, making the subscription appear empty.

## What Changes

- Track local feed item lifecycle timestamps separately from remote feed timestamps.
- Change age-based retention to use local cache lifecycle age instead of remote
  `publishedAt`.
- Keep `publishedAt` as the ordering and display timestamp for recent updates.
- Preserve favorite and read-later protection during normal retention cleanup.
- Add migration behavior for existing cached items that only have `publishedAt` and
  `fetchedAt`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `link-rss-feed`: Feed item storage and retention requirements change so local cache
  lifecycle age, not remote publication time, drives age-based cleanup.

## Impact

- Backend RSS cache model and Nitrite persistence for feed items.
- Feed refresh upsert behavior for first-seen and last-seen lifecycle timestamps.
- Retention cleanup behavior and tests.
- Generated API models may gain additional feed item timestamp fields if lifecycle
  timestamps remain visible through the Console API.
