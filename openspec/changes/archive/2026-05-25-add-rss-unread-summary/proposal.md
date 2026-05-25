## Why

The RSS subscription sidebar currently mixes two different signals: cached item counts for the "all updates" entry and feed health icons for individual subscriptions. Users need a quick way to see which subscriptions still have unread updates, while feed health belongs near the selected reading context where it can open richer diagnostics.

## What Changes

- Add a lightweight Console RSS summary capability that reports unread item counts for all subscriptions and each RSS-enabled link.
- Use unread counts in the subscription sidebar right-side slot for both "全部动态" and individual subscription entries.
- Keep cached item counts as secondary metadata in the selected RSS updates header instead of using them as the sidebar number.
- Move the selected subscription's RSS health badge from the sidebar into the right-side RSS updates header near the selected source title.
- Make the RSS health badge open a feed status detail modal with aggregate link RSS state and per-feed URL state.
- Support an aggregate "全部动态" status summary when no single subscription is selected.
- Refresh unread summaries after item read-state changes, bulk mark-read actions, feed refreshes, and cache cleanup.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `link-rss-feed`: Extend Console RSS reading requirements with unread-count summaries in the subscription sidebar and clickable RSS status details in the selected feed header.

## Impact

- Backend RSS feed item API: add a lightweight unread-count summary endpoint or equivalent generated Console API response.
- Embedded feed item store: add count helpers for unread cached items by link name and in aggregate.
- Console RSS updates view: update subscription sidebar counts, selected-source metadata, RSS status badge placement, detail modal, and query invalidation.
- API client: regenerate `console/src/api/generated/` after adding the backend endpoint or response model.
- Tests: add store/API coverage for unread summary counts and focused frontend tests for count/status helper behavior where practical.
