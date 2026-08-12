## Context

RSS feed items are operational cache records keyed by Link metadata name and stored in the
plugin-local SQLite database. A Link with `spec.rss.enabled=false` is excluded from scheduled and
manual refreshes, but its existing items and `status.rss` remain. Because Console source resolution
only loads enabled subscriptions, those items are then rendered as belonging to a deleted link.

Link deletion already uses `LinkReconciler` and a finalizer to call the store-level
`deleteByLinkName` operation. Unsubscribing is different from deletion: the Link and its feed URL
configuration remain, while only operational RSS data is discarded. Cleanup must also account for
a refresh that fetched the Link while enabled and completes after it has been disabled.

## Goals / Non-Goals

**Goals:**

- Remove every cached feed item and all RSS runtime status when a Link is no longer subscribed.
- Apply cleanup independently of whether the Link was edited through Console or the Extension API.
- Preserve feed URLs on an explicitly disabled RSS configuration for convenient re-enablement.
- Ensure an in-flight refresh cannot leave cache records or runtime status after unsubscribe.
- Make the destructive behavior explicit in Console and refresh affected client-side queries.

**Non-Goals:**

- Do not add per-item physical deletion; hidden items remain the mechanism for excluding individual
  entries while a subscription is enabled.
- Do not add a custom unsubscribe endpoint, generated API client operation, or database migration.
- Do not preserve favorite, read-later, hidden, or read state after the owning subscription ends.
- Do not remove feed URLs merely because RSS tracking is disabled.

## Decisions

1. Treat current desired state as authoritative in Link reconciliation.

   For every live Link whose RSS configuration is absent or not enabled, reconciliation deletes
   cached items by metadata name and clears `status.rss`. This is based on current state rather than
   detecting a specific true-to-false transition, so startup synchronization also repairs stale
   cache created before this change and direct Extension API writes receive the same behavior.

   Cleanup remains idempotent. Cache deletion happens before status is cleared; if storage cleanup
   fails, the reconciler must not persist an apparently clean status. Existing deletion-finalizer
   behavior remains unchanged.

   **Alternative considered:** perform cleanup only in `LinkEditingModal`. This would provide a
   simpler immediate UI flow but would miss non-Console writers and would not repair existing stale
   data.

2. Recheck subscription state when a refresh completes.

   A refresh may pass its initial enabled check, perform network and SQLite work, and then race with
   unsubscribe reconciliation. Before applying success or failure status, refresh completion must
   fetch the latest Link. If that Link is no longer subscribed, it performs the same cache cleanup,
   leaves `status.rss` clear, and does not publish the stale refresh result. This second guard makes
   the final state independent of whether reconciliation or the refresh finishes first.

   Cache writes and cleanup are serialized by Link name across refresh and reconciliation. This
   ensures cleanup based on a disabled Link snapshot finishes before a newly re-enabled subscription
   can write its initial cache, without serializing operations for unrelated Links.

   **Alternative considered:** rely only on the next reconciliation event. That is eventually
   consistent in the common case, but a refresh could recreate rows after cleanup and then fail to
   update the Link, leaving no later event guaranteed to remove them.

3. Preserve configuration but discard all operational RSS state.

   When `spec.rss` remains present with `enabled=false`, `feedUrls` remain unchanged. Cleanup removes
   all rows for the Link, regardless of read, favorite, read-later, or hidden flags, and sets
   `status.rss` to null. Re-enabling therefore starts from an empty cache and the existing initial
   refresh flow repopulates current upstream items without stale validators or counts.

4. Confirm only a real unsubscribe transition in Console.

   Saving a Link that changes RSS from enabled to disabled requires confirmation describing the
   irreversible removal of all cached and saved RSS items. Cancelling the confirmation performs no
   patch. Successful unsubscribe invalidates Link subscription queries, feed item lists, unread
   summaries, and the unified item summary so later views reload backend state. Editing an already
   disabled Link does not repeatedly prompt.

   **Alternative considered:** confirm immediately when the checkbox is cleared. Confirming at save
   time keeps unsaved form interaction reversible and matches when the destructive state change is
   actually submitted.

## Risks / Trade-offs

- [Risk] Reconciliation is asynchronous relative to the core Link patch response, so Console may
  briefly observe old aggregate counts. → Invalidate all affected queries after save and make the
  backend lifecycle, rather than transient UI state, the source of truth.
- [Risk] A large cache deletion can occupy the synchronous reconciler path. → Reuse the indexed
  `deleteByLinkName` SQLite operation and keep cleanup scoped to one Link.
- [Risk] Storage failure leaves an unsubscribed Link with stale operational data. → Delete cache
  first, do not clear status on failure, and allow later reconciliation or startup sync to retry.
- [Risk] Users may expect favorites or read-later items to survive. → State explicitly in the
  confirmation that all cached RSS data is permanently removed.

## Migration Plan

No schema or data migration is required. On deployment, controller startup synchronization visits
existing Links and removes stale RSS data from Links that are already unsubscribed. Rollback does
not require data conversion, but data removed by unsubscribe cannot be restored except by enabling
RSS and fetching it again from upstream sources.

## Open Questions

None.
