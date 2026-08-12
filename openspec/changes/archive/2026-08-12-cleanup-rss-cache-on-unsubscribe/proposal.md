## Why

Disabling RSS tracking currently stops future refreshes but leaves the link's cached feed items in
SQLite. Those orphaned items remain in aggregate RSS views and appear to come from a deleted link,
even though the Link itself still exists.

## What Changes

- Treat changing a Link from an enabled RSS subscription to a disabled or absent RSS configuration
  as an unsubscribe lifecycle event.
- Delete all cached feed items owned by the unsubscribed Link, including hidden, favorite, and
  read-later items, and clear its `status.rss` runtime state.
- Preserve configured feed URLs while RSS is disabled so the subscription can be enabled again.
- Prevent a refresh already in progress from restoring feed items or RSS status after the Link has
  been unsubscribed.
- Warn Console users that unsubscribing permanently removes cached RSS data and refresh affected RSS
  queries after a successful save.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `link-rss-feed`: Define unsubscribe cleanup semantics, including cached item removal, RSS status
  cleanup, preservation of feed URL configuration, and in-flight refresh handling.

## Impact

- Backend Link reconciliation and RSS refresh completion behavior.
- Plugin-local SQLite feed item data and Link `status.rss` state.
- Console link editing confirmation and Vue Query cache invalidation.
- No new API endpoint, generated client change, database migration, or dependency is required.
