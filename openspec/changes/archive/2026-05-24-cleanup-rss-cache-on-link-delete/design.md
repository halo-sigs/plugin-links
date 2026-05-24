## Context

RSS/Atom feed items are stored in the plugin-local Nitrite collection rather than as Halo Extension resources. The stored item identity is scoped by link name and feed URL, and the Console RSS updates view can list all cached items without resolving every item back to a live `Link`.

Deleting a `Link` currently goes through Halo's generated Extension API. The plugin has no Link-specific delete endpoint in that path, so cleanup attached only to Console delete handlers would miss other deletion callers. Adjacent plugins, especially `plugin-moments`, handle delete-time side effects with a `Reconciler` and finalizer: the reconciler observes `deletionTimestamp`, performs cleanup, then removes its finalizer so deletion can complete.

## Goals / Non-Goals

**Goals:**

- Ensure cached feed items for a `Link` are removed when that `Link` is deleted.
- Handle deletion consistently for Console deletes, batch deletes, group deletes with `deleteLinks=true`, and any direct Extension API delete.
- Use the established Halo reconciler/finalizer pattern for delete-time cleanup.
- Keep the RSS feed item listing and retention APIs unchanged.

**Non-Goals:**

- Do not introduce a new feed subscription Extension or move feed items out of Nitrite.
- Do not add a user-facing cleanup button or change Console delete confirmation copy.
- Do not preserve favorite or read-later items for a deleted link.
- Do not scan and repair historical orphaned RSS items left by links deleted before this finalizer exists.

## Decisions

1. Add a `LinkReconciler` with a plugin-owned finalizer.

   The reconciler watches `Link` resources. For non-deleted links, it adds a finalizer such as `link.halo.run/rss-cache-cleanup` when absent. For deleted links, it deletes cached RSS items by `metadata.name`, removes that finalizer, and updates the `Link` resource.

   This follows the `plugin-moments` pattern and covers every delete caller because the cleanup is tied to the resource lifecycle rather than a specific endpoint or UI path.

2. Add a store-level `deleteByLinkName(String linkName)` operation.

   `LinkFeedItemStore` should expose a direct deletion operation, implemented in `NitriteLinkFeedItemStore` using the existing `linkName` index. This keeps deletion logic inside the feed cache abstraction and avoids leaking Nitrite filters into the reconciler.

3. Delete all cached items for the link, including saved items.

   Favorite and read-later protection applies to normal retention cleanup because the link still exists and the user intentionally saved those items. Once the owning `Link` is deleted, its RSS subscription is gone, so keeping saved items would leave orphaned records in "all updates" with no live source link.

4. Use startup reconciliation to attach finalizers to existing links.

   The reconciler should let the controller sync existing live `Link` resources on startup, so links created before this change become protected after the plugin starts. Historical orphaned cache records from links already deleted before this change are outside this proposal.

## Risks / Trade-offs

- [Risk] A link deleted before the reconciler has attached the new finalizer can still leave orphaned cache items. -> Mitigate by using startup sync for existing links and by adding focused tests for finalizer attachment.
- [Risk] Nitrite cleanup runs in the synchronous reconciler path and may remove many items for a heavily subscribed link. -> Mitigate with indexed deletion by `linkName` and keep the operation scoped to one link.
- [Risk] Cleanup failure can block Link deletion while the finalizer remains. -> This is intentional for data consistency; the controller retry mechanism should retry reconciliation after transient failures.
- [Risk] Existing orphaned items remain after upgrade. -> Keep this out of scope for the lifecycle fix; a later maintenance task can add an explicit orphan sweep if needed.

## Migration Plan

Deploying the change starts the new reconciler. Startup reconciliation attaches the finalizer to existing live links; no data migration is required. Rollback leaves already-attached finalizers as a consideration, so rollback should happen only after either the old plugin can ignore/remove that finalizer or the new plugin has removed it during normal deletions.

## Open Questions

- None for the proposed scope.
