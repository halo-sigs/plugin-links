## 1. Backend Feed Item State

- [x] 1.1 Add `favorite` and `readLater` fields to `LinkFeedItem` and `LinkFeedItemQuery`.
- [x] 1.2 Update `LinkFeedItemStore` with favorite and read-later state update methods.
- [x] 1.3 Add Nitrite indexes for `favorite` and `readLater`, and backfill missing values to `false`.
- [x] 1.4 Preserve `read`, `favorite`, and `readLater` values when refreshed items are upserted.
- [x] 1.5 Extend feed item listing filters to support favorite and read-later state.

## 2. Backend Retention And API

- [x] 2.1 Update age, per-link-count, and global-count retention cleanup to skip favorite or read-later items.
- [x] 2.2 Add Console endpoints for toggling favorite and read-later state on cached feed items.
- [x] 2.3 Add favorite and read-later query parameters to the feed item listing endpoint.
- [x] 2.4 Update RBAC role templates for the new item state update endpoints.
- [x] 2.5 Regenerate OpenAPI docs and the generated TypeScript API client.

## 3. Console UI

- [x] 3.1 Extend `useLinkFeedItems` with saved-state filters and toggle actions.
- [x] 3.2 Add favorite and read-later controls to each item in `LinkFeedList.vue`.
- [x] 3.3 Add favorite and read-later filtering controls to the RSS updates view.
- [x] 3.4 When opening an external article URL, mark the item read and clear read-later while preserving favorite.
- [x] 3.5 Keep the current link, group, read-state, cursor pagination, and loading behavior working with the new filters.

## 4. Tests And Verification

- [x] 4.1 Add store tests for default saved-state migration, filtering, toggling, and upsert preservation.
- [x] 4.2 Add retention tests proving favorite and read-later items are not deleted by age or count cleanup.
- [x] 4.3 Add service or endpoint tests for passing favorite and read-later filters and returning not-found for missing item updates.
- [x] 4.4 Run `./gradlew test`.
- [x] 4.5 Run `pnpm --dir console type-check`.
- [x] 4.6 Run `pnpm --dir console lint`.
- [x] 4.7 Run `./gradlew build`.
- [x] 4.8 Validate the OpenSpec change with `openspec validate add-feed-item-saved-states --strict`.
