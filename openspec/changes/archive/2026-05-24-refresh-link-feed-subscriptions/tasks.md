## 1. Refresh Orchestration

- [x] 1.1 Confirm the generated Console link feed API exposes `refreshLinkFeed` for single-link remote refresh and keep the implementation frontend-only unless a backend batch endpoint becomes necessary.
- [x] 1.2 Add a small frontend refresh helper/composable that accepts one or more RSS-enabled `Link` objects and calls `refreshLinkFeed` for each link.
- [x] 1.3 Ensure refresh-all preserves partial success by continuing after individual link refresh failures and collecting success/failure counts.
- [x] 1.4 Expose loading state for current-subscription refresh and all-subscriptions refresh so duplicate refreshes are prevented.

## 2. Feed Page UI

- [x] 2.1 Derive the currently selected `Link` in `LinkFeedList.vue` from `selectedLinkName` and the existing RSS link list.
- [x] 2.2 Add a remote "刷新当前" action that is enabled only when a subscription is selected and reloads the active cached item list after completion.
- [x] 2.3 Add a remote "刷新全部" action that refreshes every RSS subscription currently listed and reloads the active cached item list after completion.
- [x] 2.4 Rename cache-only item reload actions on the feed page and feed modals to "重新加载" when they do not fetch remote feed URLs.
- [x] 2.5 Show aggregate success/failure feedback after remote refresh without emitting one toast per subscription.

## 3. Validation

- [x] 3.1 Run `pnpm --dir console type-check`.
- [x] 3.2 Run `pnpm --dir console lint`.
- [x] 3.3 Run `openspec validate refresh-link-feed-subscriptions --strict`.
- [x] 3.4 Run `openspec validate link-rss-feed --strict`.
- [x] 3.5 Smoke test the friend-link feed page for current-subscription refresh, all-subscriptions refresh, and cache-only "重新加载" behavior.
