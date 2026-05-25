## 1. Cache Lifecycle Model

- [x] 1.1 Add a local `firstSeenAt` timestamp to cached feed items and persist it in the Nitrite document model.
- [x] 1.2 Backfill existing feed item documents with missing `firstSeenAt` from `fetchedAt`, falling back to migration time when no local timestamp exists.
- [x] 1.3 Preserve `firstSeenAt` when an existing stable feed item is refreshed, while continuing to update refreshed content and `fetchedAt`.

## 2. Retention Semantics

- [x] 2.1 Change age-based retention cleanup to compare the configured retention age against `firstSeenAt`.
- [x] 2.2 Keep per-link and global count-based cleanup ordered by remote item recency so cache size limits still retain the newest content.
- [x] 2.3 Preserve existing favorite and read-later protection for age-based and count-based retention cleanup.

## 3. Tests And Generated Artifacts

- [x] 3.1 Add a regression test proving an item with an old `publishedAt` but recent `firstSeenAt` survives age-based cleanup.
- [x] 3.2 Add coverage proving `firstSeenAt` is preserved across refresh upserts and backfilled for existing documents.
- [x] 3.3 Regenerate OpenAPI and Console API clients if `firstSeenAt` is exposed through the feed item API model.
- [x] 3.4 Run backend tests and strict OpenSpec validation for `align-rss-retention-with-cache-lifecycle`.
