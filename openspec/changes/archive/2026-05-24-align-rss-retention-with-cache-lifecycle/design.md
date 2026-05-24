## Context

The RSS cache stores remote feed item timestamps (`publishedAt`, `updatedAt`) and the
time of the latest refresh that produced the item (`fetchedAt`). Retention currently
uses `publishedAt` for age-based cleanup. That makes content recency double as cache
age, so a valid feed whose newest item was published more than 180 days ago can refresh
successfully and then immediately lose every unsaved cached item.

The RSS reader still needs `publishedAt` for user-facing ordering and cursor pagination.
The retention problem is narrower: the cache needs a local lifecycle timestamp that says
when the plugin first cached an item.

## Goals / Non-Goals

**Goals:**

- Distinguish remote feed timestamps from local cache lifecycle timestamps.
- Make age-based retention use local cache creation age.
- Preserve current recent-update ordering by remote `publishedAt`.
- Keep existing saved-item protections for favorite and read-later items.
- Provide safe migration behavior for existing Nitrite documents.

**Non-Goals:**

- Changing the RSS reader sorting or cursor contract.
- Adding user-facing retention settings.
- Changing scheduled refresh frequency or feed discovery behavior.
- Deleting items solely because they disappeared from a feed document.

## Decisions

### Add `firstSeenAt` as the cache-created timestamp

Feed items should gain a local lifecycle timestamp named `firstSeenAt`. It records when
the plugin first cached the stable item identity for a specific link name and feed URL.

`firstSeenAt` is intentionally separate from:

- `publishedAt`: remote article publication time, used for display and ordering.
- `updatedAt`: remote article update time, used as feed metadata.
- `fetchedAt`: latest refresh time that returned the item, kept as a last-seen style
  timestamp and for API compatibility.

Alternative considered: reuse `fetchedAt` for retention. This fixes the immediate empty
feed symptom, but because `fetchedAt` changes every successful refresh, it behaves more
like `lastSeenAt` than cache creation time and would let any item that remains in a feed
avoid age-based retention indefinitely.

Alternative considered: use `createdAt`. The term is easy to understand, but ambiguous
beside remote article creation/publication time. `firstSeenAt` makes the local cache
meaning explicit.

### Preserve `firstSeenAt` across upserts

When a refreshed item matches an existing stable ID, the store should preserve the
existing `firstSeenAt` while updating refreshed content, `publishedAt`, `updatedAt`,
`fetchedAt`, content hash, and saved states. New items should set both `firstSeenAt` and
`fetchedAt` to the refresh timestamp.

This keeps refreshes idempotent while allowing retention to measure how long the item has
been in the local cache.

### Use `firstSeenAt` for age-based retention

Age-based retention should delete unsaved items whose `firstSeenAt` is older than the
configured retention age. It should not delete an item merely because its remote
`publishedAt` is old.

Per-link and global count-based retention can continue to remove the oldest unsaved
content by `publishedAt`, because those limits are about bounding cache size while
keeping the most relevant content.

### Backfill existing documents conservatively

Existing Nitrite documents do not have `firstSeenAt`. Migration should backfill missing
`firstSeenAt` from `fetchedAt` when present, because `fetchedAt` is the best available
local cache timestamp. If `fetchedAt` is missing or invalid, use the migration time.

The migration should avoid using `publishedAt` as a fallback because that would preserve
the current bug for quiet feeds.

## Risks / Trade-offs

- **[Risk] Existing items may receive a later `firstSeenAt` than their true first cache
  time.** -> Mitigation: prefer `fetchedAt` where available and accept that the migration
  is intentionally conservative to avoid deleting valid cached items.
- **[Risk] Quiet feeds with old articles remain visible for the full retention age after
  first import.** -> Mitigation: count-based retention still bounds total and per-link
  cache size, and this behavior matches the local-cache lifecycle interpretation.
- **[Risk] Adding a timestamp field touches generated API models if exposed.** ->
  Mitigation: keep UI behavior unchanged and regenerate API clients only if the public
  feed item model includes the field.
