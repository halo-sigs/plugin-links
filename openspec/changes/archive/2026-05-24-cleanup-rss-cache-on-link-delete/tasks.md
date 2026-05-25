## 1. Feed Store Cleanup

- [x] 1.1 Add a `deleteByLinkName` operation to `LinkFeedItemStore` for removing all cached RSS/Atom items associated with a link name.
- [x] 1.2 Implement `deleteByLinkName` in `NitriteLinkFeedItemStore` using the existing `linkName` index and commit the Nitrite database after mutation.
- [x] 1.3 Add store-level tests proving link-name cleanup removes read, favorite, and read-later items for the deleted link while preserving items for other links.

## 2. Link Reconciler

- [x] 2.1 Add a `LinkReconciler` that watches `Link` resources and adds a plugin-owned RSS cache cleanup finalizer to live links.
- [x] 2.2 Handle `Link` deletion in the reconciler by deleting cached feed items for `request.name()`, removing the finalizer, and updating the `Link`.
- [x] 2.3 Configure the reconciler to sync existing links on startup so links created before this change receive the finalizer after plugin startup.

## 3. Verification

- [x] 3.1 Add reconciler tests for finalizer attachment, deletion cleanup with cached items, and deletion finalization when no cached items exist.
- [x] 3.2 Run backend tests with `./gradlew test`.
- [x] 3.3 Validate the OpenSpec change with `openspec validate cleanup-rss-cache-on-link-delete --strict`.
- [x] 3.4 Validate the updated capability with `openspec validate link-rss-feed --strict`.
- [x] 3.5 Run `git diff --check`.
