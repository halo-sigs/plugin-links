## 1. Backend unsubscribe lifecycle

- [x] 1.1 Add `LinkReconciler` tests for disabled and absent RSS configuration, stale cache and
  status cleanup, feed URL preservation, idempotent startup repair, and storage-cleanup failure.
- [x] 1.2 Extend Link reconciliation to delete all feed items before clearing `status.rss` for live
  Links that are not enabled subscriptions, without changing Link deletion finalizer behavior.
- [x] 1.3 Add RSS service tests that pause an enabled refresh, disable or remove RSS before refresh
  completion, and verify both successful and failed refresh paths leave no cache or RSS status.
- [x] 1.4 Recheck the latest Link subscription state before publishing refresh success or failure;
  run unsubscribe cleanup and discard the stale refresh result when RSS is no longer enabled.

## 2. Console unsubscribe flow

- [x] 2.1 Add colocated `LinkEditingModal` tests for confirmed unsubscribe, cancelled unsubscribe,
  edits to an already disabled Link, and invalidation of Link/feed/unread/item-summary queries.
- [x] 2.2 Add the irreversible-data warning to the enabled-to-disabled save transition and refresh
  all affected Vue Query caches after a confirmed successful update.

## 3. Verification

- [x] 3.1 Run focused backend reconciliation, RSS service, and SQLite store tests, then run the full
  Gradle test suite.
- [x] 3.2 Run Console unit tests, type checking, linting, and Prettier verification with the pinned
  pnpm toolchain.
- [x] 3.3 Run the production build, `git diff --check`, and targeted OpenSpec validation for
  `cleanup-rss-cache-on-unsubscribe` and `link-rss-feed`.
