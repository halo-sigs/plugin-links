## 1. Backend Summary API

- [x] 1.1 Add RSS unread summary response models for aggregate unread count and per-link unread counts.
- [x] 1.2 Extend `LinkFeedItemStore` with count helpers for unread items across all links and by link name.
- [x] 1.3 Implement the Nitrite unread count helpers using `read == false` filters without loading paginated item pages.
- [x] 1.4 Add store tests covering aggregate counts, per-link counts, read-item exclusion, and zero-unread behavior.
- [x] 1.5 Add a `LinkFeedEndpoint` route with OpenAPI metadata that returns the unread summary on bounded-elastic execution.

## 2. Generated Client and Query State

- [x] 2.1 Run `./gradlew generateApiClient` so the unread summary API is available in `console/src/api/generated/`.
- [x] 2.2 Add a Vue Query composable for RSS unread summaries with a stable query key.
- [x] 2.3 Invalidate the unread summary query after per-item read toggles, bulk mark-read, manual feed refresh, and feed cleanup.
- [x] 2.4 Keep favorite and read-later mutations from invalidating the unread summary unless they also change read state.

## 3. Sidebar and Header UI

- [x] 3.1 Update `LinkFeedSubscriptionSidebar.vue` so the all-updates entry shows aggregate unread count instead of cached item count.
- [x] 3.2 Update subscribed link rows so the right-side slot shows each link's unread count and hides or mutes zero counts.
- [x] 3.3 Remove per-link RSS health badges from the sidebar rows.
- [x] 3.4 Update `LinkFeedList.vue` selected-source metadata to keep cache context and include unread context where useful.
- [x] 3.5 Add a selected-source RSS status badge near the RSS updates header title for both selected-link and all-updates states.

## 4. Status Details Modal

- [x] 4.1 Extract reusable RSS status classification helpers for success, warning, failure, and waiting states.
- [x] 4.2 Add a status details modal for a selected link showing configured feed URLs, aggregate RSS state, cache count, unread count, timestamps, failure count, and last error.
- [x] 4.3 Add aggregate all-updates modal content summarizing subscription count, cached item count, unread count, and warning/failure subscriptions.
- [x] 4.4 Ensure modal timestamps use existing Halo shared date helpers and long URLs/errors wrap without breaking the layout.

## 5. Verification

- [x] 5.1 Run focused backend tests for the feed item store and endpoint changes.
- [x] 5.2 Run `./gradlew generateApiClient` and confirm generated API diffs match the new summary contract.
- [x] 5.3 Run `pnpm --dir console type-check`, `pnpm --dir console lint`, and relevant frontend unit tests.
- [x] 5.4 Run `./gradlew test` or the narrowest Gradle test set that covers backend changes.
- [x] 5.5 Run `openspec validate add-rss-unread-summary --strict`.
- [x] 5.6 Smoke-test `/console/links/rss` in Halo Console to confirm sidebar unread counts, selected-source status badge, and details modal behavior.
