## Context

RSS items are stored in the plugin-local SQLite `link_feed_items` table and identified by a stable
hash of link name, feed URL, and source-local identity. Refreshes upsert the latest source entries,
so deleting a cache row does not durably remove an item that remains in the feed. The same store
backs the Console reader, public REST feed queries, and the theme Finder.

The current item model has read, favorite, and read-later states but no site-level visibility
state. Normal retention protects favorite and read-later items, while unread summaries and public
queries currently count or return every matching cache row. The Console uses cursor-based infinite
loading, so it has no stable page-wide selection concept.

SQLite schema version 1 is already deployed. Startup creates missing schema objects through
`createSchema`, validates active databases and snapshots by `user_version` plus `quick_check`, and
restores an older valid snapshot before opening it as the active database.

## Goals / Non-Goals

**Goals:**

- Add reversible, site-level hiding for exact cached RSS item identities.
- Keep hidden items out of ordinary Console, saved-item, anonymous REST, and Finder results.
- Preserve hidden and saved states across refreshes and protect hidden items from normal retention.
- Support single-item and explicit-ID batch hide/unhide through one transactional store interface.
- Provide an accurate hidden count and a dedicated Console recovery list.
- Upgrade existing SQLite files idempotently at startup without a schema-version migration.

**Non-Goals:**

- No physical-delete workflow for selected items.
- No cross-feed URL matching, URL canonicalization, content-hash deduplication, or inferred hiding.
- No per-user visibility state, expiry policy, plugin setting, or anonymous hidden-item query.
- No select-all operation over unloaded or filter-matched items.
- No Issue, pull-request, release, or deployment automation in this change.

## Decisions

### 1. Store hidden state on the cached feed item

Add a non-null SQLite boolean column and corresponding `LinkFeedItem.hidden` field:

```text
hidden INTEGER NOT NULL DEFAULT 0 CHECK (hidden IN (0, 1))
```

New items default to visible. An upsert that refreshes an existing stable item MUST update remote
content without overwriting `hidden`, just as it preserves read, favorite, and read-later states.
Changing the source identity or feed URL produces a different stable ID and therefore a new visible
item.

**Rationale**: Hidden is an item state, and keeping it on the row gives the store adapter simple,
indexable filters with no second persistence seam.

**Alternatives considered**:

- Physical deletion plus a tombstone table would duplicate identity lifecycle rules and could not
  show the complete item in the recovery list without retaining another copy.
- Hidden markers in `storage_metadata` avoid a column but turn ordinary listing, counting, and
  retention into correlated metadata lookups for a state that naturally belongs to the item.

### 2. Ensure the additive column inside `createSchema`

Fresh table creation includes `hidden`. For an existing table, `createSchema` checks
`PRAGMA table_info(link_feed_items)` and executes guarded `ALTER TABLE ... ADD COLUMN hidden ...`
only when the column is absent. It then creates an idempotent hidden/recent index. The current
`SCHEMA_VERSION = 1` and `PRAGMA user_version` remain unchanged.

This approach deliberately treats the column as an idempotent additive compatibility step:

- an existing v1 database converges when opened;
- repeated startup is a no-op after the first successful addition;
- a restored v1 snapshot is accepted by existing validation and converges when opened as active;
- a failed addition leaves RSS unavailable for that run and is retried at the next startup without
  isolating or replacing the valid database as corrupt.

**Rationale**: A single additive defaulted column does not justify a versioned migration framework,
and the current open path is already the common point for new, active, restored, and imported SQLite
files.

**Alternatives considered**:

- Bumping to schema v2 would require validation and restoration to distinguish an upgradeable v1
  file from an invalid file before current-version checks quarantine it.
- Leaving two unguarded `ALTER TABLE` executions is not idempotent because a later startup would
  fail on the existing column.

### 3. Keep visibility behavior behind the feed item store interface

Extend the store interface with hidden-aware query state, an exact hidden count, and one batch state
operation. Callers do not know about SQLite column checks or update mechanics.

Console item listing accepts an optional `hidden` query parameter, but omission means `false` rather
than "all states". The dedicated hidden list requests `hidden=true`. The public query module and
Finder always force visible-only store queries and do not expose `hidden` on public value objects.

Filtering happens in SQLite before cursor pagination. Frontend filtering is rejected because it
would produce short pages, invalid cursors, inaccurate empty states, and public-data leaks.

### 4. Use one explicit-ID batch command for hide and unhide

Expose a Console-only command:

```text
POST /apis/console.api.link.halo.run/v1alpha1/rss/items/-/hidden
{
  "ids": ["stable-id-1", "stable-id-2"],
  "hidden": true
}
```

The operation deduplicates IDs, rejects an empty ID set, and applies the desired state in one SQLite
transaction. Unknown IDs are ignored. A database error rolls back the whole batch. The successful
response reports the deduplicated request count and only the rows whose state actually changed:

```text
{
  "requestedCount": 2,
  "updatedCount": 1
}
```

One ID provides the single-item behavior. No arbitrary product-level batch cap is added; the store
implementation can use prepared updates inside the transaction without relying on one unbounded
SQLite `IN` expression.

**Rationale**: A single deep command keeps validation, idempotency, transactionality, and result
semantics identical for the item and selection-mode interfaces.

### 5. Separate visibility counts from physical cache counts

Hidden items:

- do not appear in normal Console, favorite, or read-later lists;
- do not contribute to aggregate or per-link unread counts;
- do not participate in normal mark-all-read operations;
- do not appear in public REST or Finder results;
- do contribute to physical per-feed, per-link, and global cache counts;
- still make a feed cache non-empty for conditional-request decisions.

Add a dedicated Console hidden-count operation rather than adding unrelated data to the unread
summary. Normal retention excludes hidden items alongside favorite and read-later items. Hidden
items can therefore make physical cache counts exceed normal retention targets. Link deletion still
removes every row for that link, including hidden rows.

### 6. Provide a focused Console hidden-item workflow

Add an "已隐藏" action beside the existing favorite and read-later actions. It opens a cursor-loaded
modal ordered by publication time. The modal displays saved/read state but only offers external open,
single unhide, and batch unhide actions.

The primary list provides a per-item hide action and an explicit batch-selection mode. Entering the
mode reveals checkboxes for loaded items plus "全选已加载", cancel, and hide actions. Selection is
cleared when subscription/read filters change, data is reloaded, or a mutation succeeds.

Single hide, batch hide, and batch unhide require confirmation. Single unhide executes directly.
Opening an item from the hidden list keeps it hidden, marks it read, removes read-later, and preserves
favorite, matching the existing open-item behavior.

After a successful mutation, invalidate and refetch ordinary item queries, hidden item queries,
unread summaries, and the hidden count. HTTP failures continue through Halo's global interceptor;
the feature adds only success feedback and confirmation copy.

## Risks / Trade-offs

- **[Risk] Hidden rows can grow beyond retention targets** -> **Mitigation**: Hiding requires an
  explicit Console action, the hidden count remains visible, and deleting the owning Link removes
  the rows. Bulk permanent cleanup remains a separate future decision.
- **[Risk] Schema version 1 has pre- and post-feature physical shapes** -> **Mitigation**:
  `createSchema` always performs a guarded column check before the store becomes available, and
  compatibility tests cover new, existing, repeated, restored, and failed startup paths.
- **[Risk] A downgraded plugin ignores hidden semantics** -> **Mitigation**: The additive column is
  readable by older code, but semantic downgrade is not guaranteed; document that older code may
  display or retain-clean hidden items.
- **[Risk] Refresh races with hide/unhide** -> **Mitigation**: SQLite callbacks are serialized, the
  batch change is transactional, and refresh upserts never update `hidden`.
- **[Risk] Stale selections contain missing item IDs** -> **Mitigation**: The command is idempotent,
  ignores missing IDs, reports actual changes, and refetches all affected query state.
- **[Risk] Visibility filtering can break pagination when applied late** -> **Mitigation**: Apply the
  hidden predicate in store SQL before ordering and cursor limits.

## Migration Plan

1. Extend the item model and SQLite schema creation with the guarded hidden column and index.
2. Add hidden-aware store queries, counts, retention predicates, and transactional state updates.
3. Add Console request/result models, listing/count/state routes, and public/Finder exclusions.
4. Regenerate the OpenAPI TypeScript client before changing Console callers.
5. Add Console query/mutation state and the primary/hidden-list interactions.
6. Verify new, existing, repeated, restored, and failed schema-initialization cases plus backend,
   public, Finder, frontend, build, and local Halo interaction scenarios.

Rollback does not require removing the additive column. Older code can read rows with the extra
column, but it does not honor hidden visibility or retention semantics; rolling back the plugin is
therefore not a supported way to preserve hiding behavior.

## Open Questions

- None. The product and implementation boundaries were confirmed before this proposal.
