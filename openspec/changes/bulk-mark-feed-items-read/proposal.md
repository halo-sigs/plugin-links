## Why

The current "mark all read" action only updates unread feed items that are already loaded in the current Console page. Because the RSS updates list is paginated in batches of 30, users with many unread items still need to repeatedly load more pages and click the same action.

## What Changes

- Add a backend bulk mark-read capability for cached RSS/Atom feed items.
- Allow the Console to mark every unread item as read across all subscriptions.
- Allow the Console to mark every unread item as read for the selected link subscription.
- Return a mutation summary so the Console can report how many unread items were updated.
- Update the Console "全部标为已读" action to call the backend bulk operation instead of issuing per-item requests for only the loaded page.
- Keep existing per-item read, favorite, and read-later state updates unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `link-rss-feed`: Change the Console mark-read workflow from a current-page operation to a backend bulk operation scoped to all subscriptions or the selected link subscription.

## Impact

- Backend RSS feed item API: add a bulk mark-read endpoint and generated OpenAPI contract.
- Embedded feed item store: add an efficient bulk update path that filters unread items by optional link name.
- Console RSS updates view: update confirmation text, loading state, success feedback, and cache reload behavior.
- API client: regenerate `console/src/api/generated/` after adding the backend endpoint.
- Tests: add store-level coverage for bulk read-state updates and focused backend/API behavior where practical.
