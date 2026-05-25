## Context

The RSS updates Console currently implements "全部标为已读" entirely on the client. It derives unread item IDs from the already loaded page, then calls `markLinkFeedItemRead` for those IDs in small chunks. This matches the existing `Console current-list mark-read action` requirement, but it breaks down once a site has more unread items than the current cursor page because users have to keep loading older batches before the action can reach them.

The cached RSS item store already persists read state in Nitrite and supports indexed filtering by `linkName` and `read`. The new workflow should use that store directly from the backend instead of treating pagination as the mutation boundary.

## Goals / Non-Goals

**Goals:**

- Provide a backend operation that marks all unread cached feed items as read.
- Support two scopes: all RSS subscriptions and the currently selected link subscription.
- Return a count of updated items so the Console can give accurate feedback.
- Replace the current client-side per-loaded-item loop with one generated API call.
- Preserve existing per-item read, favorite, and read-later workflows.

**Non-Goals:**

- Bulk-marking items unread.
- Bulk operations scoped by group, favorite, read-later, feed URL, or cursor.
- Changing feed refresh, retention, or saved-item semantics.
- Adding a new feed/subscription entity.

## Decisions

1. Add a Console API endpoint for bulk mark-read.

   Use a backend endpoint such as `POST rss/items/-/read` under the existing `LinkFeedEndpoint` with an optional `linkName` query parameter. Omitting `linkName` means all cached feed items; providing `linkName` limits the mutation to cached items for that link. The endpoint returns a small DTO, for example `LinkFeedMarkReadResult`, with `updatedCount`.

   Alternative considered: keep the current per-item endpoint and have the client page through every unread item. That would still couple mutation correctness to pagination and would make the frontend issue many API requests for large caches.

2. Make the store perform the bulk update in one operation.

   Extend `LinkFeedItemStore` with a method such as `markUnreadAsRead(String linkName)` that filters `read == false` and optionally `linkName == <selected>`, updates those documents to `read = true`, commits once, and returns the update count. This keeps the expensive part inside the embedded store and avoids loading every page into the UI.

   Alternative considered: call `listRecent()` repeatedly from a service loop. That would reuse existing code, but the current query object is intentionally cursor/limit oriented and would still require extra pagination logic to be correct.

3. Keep the operation scoped to link subscriptions, not feed URLs.

   A `Link` can contain multiple feed URLs but appears once in the subscription sidebar. The user-facing scopes are "全部订阅" and the selected link, so the backend should follow `linkName` rather than exposing per-feed identities.

   Alternative considered: add a `feedUrl` filter. That would add UI and API surface that the current reader deliberately hides.

4. Update the Console action to describe scope instead of loaded count.

   The confirmation should no longer say "current loaded items" or warn that older unloaded items are excluded. It should say whether the action will mark all unread items for all subscriptions or the selected subscription. After the backend responds, the Console reloads the active feed list and reports the returned count.

   Alternative considered: add a preflight count endpoint to show the exact number before confirmation. That would improve the dialog copy but adds another API call and is not necessary for this workflow.

## Risks / Trade-offs

- Bulk updates may scan many cached records -> use indexed `read` and `linkName` filters in Nitrite, update only unread documents, and commit once.
- A feed refresh can insert new unread items while the bulk update is running -> define the operation over the items matched during the backend update; newly inserted items after that remain unread.
- Users may trigger the action when no unread items exist -> return `updatedCount = 0` and show neutral feedback rather than treating it as an error.
- The generated client can drift from the backend endpoint -> run `./gradlew generateApiClient` after the backend API changes and update frontend imports from `console/src/api/generated/`.

## Migration Plan

No data migration is required. Existing cached items already carry a `read` boolean, and the new operation only updates that existing field.

Rollback is also straightforward: removing the bulk endpoint and reverting the Console to the per-item path leaves the stored data shape unchanged.

## Open Questions

None.
