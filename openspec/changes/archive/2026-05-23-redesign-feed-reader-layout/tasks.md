## 1. Reader Layout

- [x] 1.1 Refactor `LinkFeedList.vue` so the route view composes the RSS reader layout instead of rendering one vertical filter-and-list flow.
- [x] 1.2 Add a subscription sidebar with an all-updates entry and subscribed links from `useRssLinksFetch()`.
- [x] 1.3 Wire sidebar selection to the existing `linkName` filter and keep the all-updates entry clearing that filter.
- [x] 1.4 Remove the RSS updates group selector and any route-level dependency on `selectedGroupName` / `selectGroup`.

## 2. Feed Controls And Lists

- [x] 2.1 Replace the read-state select with all/unread/read tabs above the article list.
- [x] 2.2 Keep article list loading, empty, item card, state toggle, open-item, and load-more behavior working in the new right-column content area.
- [x] 2.3 Move read-later presentation out of the primary right-column sequence while preserving a separate way to view read-later items.
- [x] 2.4 Keep favorite and read-later state synchronization across visible feed surfaces after item mutations.

## 3. Responsive UX And Verification

- [x] 3.1 Ensure the two-column layout adapts on narrow Console viewports without overlapping text or controls.
- [x] 3.2 Run `pnpm --dir console type-check`.
- [x] 3.3 Run `pnpm --dir console lint`.
- [x] 3.4 Validate the OpenSpec change with `openspec validate redesign-feed-reader-layout --strict`.
- [x] 3.5 Smoke test the RSS updates view in a browser and confirm the subscription list, read-state tabs, article list, and saved-item flows behave correctly.
