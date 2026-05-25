## Why

The friend-link feed page currently labels a cache reload action as "刷新", but it only re-queries cached items and does not fetch upstream RSS/Atom feeds. This makes the primary page action misleading and leaves users without a clear way to manually update all subscriptions or the currently selected subscription from the feed-reading workflow.

## What Changes

- Add a Console workflow on the friend-link feed page to refresh remote RSS/Atom feeds for the currently selected subscription.
- Add a Console workflow on the friend-link feed page to refresh remote RSS/Atom feeds for all enabled subscriptions.
- Reload the cached feed item list after remote refresh completes so newly fetched items appear without requiring a separate manual reload.
- Keep a distinct cache-only reload behavior where needed, with UI text that does not imply remote fetching.
- Surface per-subscription refresh progress and failures in a way that does not block successful subscriptions from updating.

## Capabilities

### New Capabilities

### Modified Capabilities
- `link-rss-feed`: Add Console-level requirements for refreshing the current RSS subscription or all RSS subscriptions from the friend-link feed page.

## Impact

- Frontend: `console/src/views/LinkFeedList.vue`, RSS feed composables, generated API usage, and refresh button/loading/error states.
- Backend/API: may require a batch refresh endpoint for all enabled RSS links if the Console should avoid issuing many single-link requests directly.
- OpenAPI/generated client: regenerate the TypeScript API client if a new backend endpoint is added.
- Tests: add focused backend endpoint/service tests for batch refresh if added, and frontend type/lint coverage for the new Console workflow.
