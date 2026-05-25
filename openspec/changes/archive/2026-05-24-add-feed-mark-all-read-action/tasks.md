## 1. Mark-Read Orchestration

- [x] 1.1 Derive the currently loaded unread feed item IDs from the primary `mainFeed.items` list.
- [x] 1.2 Add a frontend bulk mark-read helper that calls the generated single-item mark-read API with bounded request batches.
- [x] 1.3 Expose pending state and affected-count reporting so duplicate mark-all-read actions are prevented.
- [x] 1.4 Reload the active primary feed list after the batch completes while preserving the selected subscription and read-state filter.

## 2. Feed Page UI

- [x] 2.1 Add a "全部标为已读" action to the primary friend-link feed toolbar.
- [x] 2.2 Disable or no-op the action when the currently loaded primary list has no unread items.
- [x] 2.3 Show a confirmation dialog that includes the number of currently loaded unread items and explains the current-loaded-list scope.
- [x] 2.4 Show one completion summary after the action finishes without adding duplicate HTTP failure toasts.
- [x] 2.5 Keep the action scoped to the primary feed list and leave favorites/read-later modal controls unchanged.

## 3. Validation

- [x] 3.1 Run `pnpm --dir console type-check`.
- [x] 3.2 Run `pnpm --dir console lint`.
- [x] 3.3 Run `openspec validate add-feed-mark-all-read-action --strict`.
- [x] 3.4 Smoke test the friend-link feed page confirmation, disabled/no-unread state, and successful current-list mark-read behavior in the Halo Console.
