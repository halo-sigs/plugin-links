## 1. SQLite item state and store behavior

- [x] 1.1 Add schema compatibility tests for a new database, an existing v1 database without the
  hidden column, repeated startup, restoration of an older valid snapshot, and a failed column or
  index addition that preserves the active database for retry.
- [x] 1.2 Add `LinkFeedItem.hidden`, create the column and hidden/recent index for new databases,
  and implement the guarded `PRAGMA table_info` plus `ALTER TABLE` compatibility step while keeping
  `SCHEMA_VERSION` at 1.
- [x] 1.3 Add store tests for visible-by-default inserts, hidden-state preservation during upsert,
  exact-ID behavior, pre-pagination hidden filtering, combined filters, and exact hidden counts.
- [x] 1.4 Extend `LinkFeedItemQuery` and `LinkFeedItemStore`, then implement SQLite row binding,
  parsing, visible/hidden listing, hidden counting, and transactional idempotent batch state updates.
- [x] 1.5 Add tests proving batch requests deduplicate IDs, ignore missing IDs, count only actual
  changes, preserve saved/read states, and roll back all changes on a storage failure.
- [x] 1.6 Add tests for visible-only unread summaries and mark-all-read behavior, hidden retention
  protection, physical cache counts including hidden rows, conditional-request cache detection, and
  Link deletion removing hidden rows.
- [x] 1.7 Update unread, mark-all-read, retention, cache-count, conditional-request, and Link cleanup
  store paths to satisfy the hidden-state accounting rules.

## 2. Console and public backend contracts

- [x] 2.1 Add Console endpoint tests for omitted/true hidden listing, combined query parameters,
  exact hidden count, batch hide/unhide, empty input, duplicate/missing IDs, idempotent result counts,
  and the existing storage-unavailable response.
- [x] 2.2 Add generated-contract request/result models and implement authenticated Console routes for
  `GET rss/items/-/hidden-count` and `POST rss/items/-/hidden`, with listing omission defaulting to
  visible items.
- [x] 2.3 Add public-query and Finder tests proving hidden rows never appear, cannot be requested by
  anonymous/theme callers, and do not add a hidden field to public value objects.
- [x] 2.4 Force visible-only store queries in the public REST and Finder modules while preserving
  cursor, link, and group behavior.
- [x] 2.5 Update OpenAPI operation descriptions, run `./gradlew generateApiClient`, and verify the
  generated TypeScript client exposes the Console list/count/state contracts without public hidden
  state.

## 3. Console hidden-item workflows

- [x] 3.1 Add frontend composable tests for visible and hidden cursor queries, exact hidden count,
  batch mutation results, and invalidation of normal items, hidden items, unread summaries, and the
  hidden count.
- [x] 3.2 Implement generated-client-backed Vue Query composables for hidden listing, hidden count,
  and hide/unhide mutations without adding duplicate HTTP failure toasts.
- [x] 3.3 Add component tests and reusable item-list/card inputs for explicit selection mode,
  select-all-loaded, focused hidden-item actions, and selection clearing when its context changes.
- [x] 3.4 Add the primary-list single hide action and batch-hide mode with loaded-item checkboxes,
  select-all-loaded, cancel, confirmation, success feedback, and post-success selection cleanup.
- [x] 3.5 Add the header-level "已隐藏" count entry and cursor-loaded modal with external-open,
  single-unhide, and confirmed batch-unhide actions but no saved/read-state editing controls.
- [x] 3.6 Preserve and display read/favorite/read-later states in the hidden list; ensure opening an
  article keeps it hidden, marks it read, removes read-later, and preserves favorite.
- [x] 3.7 Verify single hide, batch hide, and batch unhide confirmation copy; direct single unhide;
  empty/loading/disabled states; narrow viewport usability; and immediate refetch after success.

## 4. Documentation and verification

- [x] 4.1 Update REST and theme/Finder documentation to state that hidden items are Console-only and
  excluded from public results, and document the authenticated Console hidden count/state contracts
  where Console operations are enumerated.
- [x] 4.2 Run targeted backend tests for SQLite, store, endpoint, public-query, retention, refresh, and
  Finder behavior, then run `./gradlew test`.
- [x] 4.3 Run `pnpm test:unit`, `pnpm type-check`, formatting checks, and ESLint for the affected
  Console files, then run the Console production build.
- [x] 4.4 Run `./gradlew build` and verify regenerated API artifacts contain no unrelated or stale
  contract changes.
- [x] 4.5 Start the local Halo development environment and manually verify single/batch hide,
  select-all-loaded, hidden count/list, open behavior, single/batch unhide, refresh persistence,
  public REST exclusion, and Finder/theme exclusion.
- [x] 4.6 Run `openspec validate add-rss-feed-item-hiding --strict`, validate the affected
  `link-rss-feed` spec as applicable, run `git diff --check`, and review the final diff against every
  confirmed scope and non-goal.
