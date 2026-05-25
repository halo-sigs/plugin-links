## 1. Backend Store and API

- [x] 1.1 Add a result DTO for bulk feed item mark-read responses with an updated item count.
- [x] 1.2 Extend `LinkFeedItemStore` with a bulk method that marks unread items as read for all links or one link.
- [x] 1.3 Implement the Nitrite bulk update with `read == false` and optional `linkName` filtering, committing once and returning the updated count.
- [x] 1.4 Add store tests covering all-subscription updates, selected-link updates, already-read exclusions, and zero-update no-ops.
- [x] 1.5 Add a `LinkFeedEndpoint` route for the bulk mark-read operation with OpenAPI metadata and bounded-elastic execution.

## 2. Generated Client and Console Integration

- [x] 2.1 Run `./gradlew generateApiClient` so the new bulk endpoint is available in `console/src/api/generated/`.
- [x] 2.2 Update the mark-all-read composable to call the generated bulk API instead of batching loaded item IDs.
- [x] 2.3 Update the RSS updates view confirmation copy to describe all-subscription versus selected-link scope.
- [x] 2.4 Update success and empty-result feedback to use the backend updated count, then reload the active feed list after completion.
- [x] 2.5 Remove now-unused frontend helpers or dependencies from the old loaded-item batching path.

## 3. Verification

- [x] 3.1 Run backend tests that cover the feed item store and endpoint/service changes.
- [x] 3.2 Run frontend type-check and lint after generated client and Console updates.
- [x] 3.3 Run `./gradlew build` to verify the full plugin package.
- [x] 3.4 Run `openspec validate bulk-mark-feed-items-read --strict`.
- [x] 3.5 Smoke-test the Console RSS updates page with many unread items to confirm one action marks unloaded items read for all subscriptions and for a selected link.
