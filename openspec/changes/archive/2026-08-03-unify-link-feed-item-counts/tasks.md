## 1. Backend summary contract and storage

- [x] 1.1 Add the `LinkFeedItemSummary` response model with required `hiddenCount`, `favoriteCount`, and `readLaterCount` fields and document the visible-only predicates for saved counts.
- [x] 1.2 Replace the store-level hidden-only count operation with one SQLite summary query that independently counts all hidden rows, visible favorite rows, and visible read-later rows without changing the schema.
- [x] 1.3 Add storage tests covering zero counts, hidden rows with saved states, overlapping favorite/read-later states, and independent count results.

## 2. Console API, RBAC, and generated contracts

- [x] 2.1 Replace the `hidden-count` route with `GET rss/items/-/summary`, wire `getLinkFeedItemSummary`, remove the old response model/handler, and add endpoint tests for the three-field response and removed route.
- [x] 2.2 Update the view role template and security tests to authorize `items/summary`, remove `items/hidden-count`, and retain the existing manage-only hidden mutation boundary.
- [x] 2.3 Regenerate the OpenAPI document and TypeScript client, verify the new summary model/method, and verify no active API artifact references the removed `hidden-count` operation.
- [x] 2.4 Update `dev/rest-api.md` for the unified summary endpoint and remove the old endpoint description without modifying historical archived change artifacts.

## 3. Console summary query and header UI

- [x] 3.1 Replace the hidden-count composable and cache key with one item-summary composable that calls the generated summary client and distinguishes loading placeholders, resolved zero values, and failed requests.
- [x] 3.2 Update `LinkFeedList.vue` to render `hiddenCount`, `favoriteCount`, and `readLaterCount` on the existing header actions while keeping global saved-list filters and failure handling unchanged.
- [x] 3.3 Update view and composable tests for all three counts, explicit zero rendering, loading/error behavior, and removal of the old hidden-count client usage.
- [x] 3.4 Keep all three header entries at `(0)` during initial and background summary loading, including when stale cached data exists, then verify the resolved values replace the placeholders.

## 4. Summary invalidation and mutation coverage

- [x] 4.1 Invalidate the summary query after successful favorite and read-later mutations, including the read-later removal performed when opening an item.
- [x] 4.2 Invalidate the summary query after hide/unhide mutations while preserving normal-list, hidden-list, and unread-summary invalidations.
- [x] 4.3 Invalidate the summary query in initial/remote feed refresh and link-deletion success paths so cached counts cannot survive collection-changing operations.
- [x] 4.4 Add or update mutation-path tests proving successful operations invalidate the summary and failed operations do not report a successful refresh.

## 5. Validation

- [x] 5.1 Run targeted backend endpoint, storage, and security tests for the new summary contract and removed route.
- [x] 5.2 Run frontend tests, type-check, lint/format checks, and the production frontend build.
- [x] 5.3 Run strict targeted OpenSpec validation for the change and `link-rss-feed`, search for stale non-archive `hidden-count` references, and run `git diff --check`.
