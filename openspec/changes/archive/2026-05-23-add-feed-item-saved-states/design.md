## Context

The RSS updates feature already stores feed items in the plugin-local Nitrite database and
tracks read state as a site-level boolean on each cached item. The plugin is primarily used
by personal blog owners, so per-user reading timelines would add storage and API complexity
without matching the common deployment model.

Favorite and read-later actions are a small extension of the existing item-state model. They
should remain outside Halo Extension resources, just like feed item content and read state,
because they are high-cardinality item metadata rather than link configuration.

## Goals / Non-Goals

**Goals:**

- Add site-level favorite and read-later states to cached feed items.
- Keep favorite and read-later states independent so one item can be both saved forever and
  queued for later reading.
- Preserve saved states when a feed refresh upserts an existing item.
- Let Console users filter the RSS updates list by favorite and read-later state.
- Protect favorite and read-later items from normal RSS cache retention cleanup.
- Remove an item from read-later automatically when the user opens its external article URL.

**Non-Goals:**

- No per-Halo-user saved state or personal timelines.
- No public theme-facing saved-items page.
- No folder/tag system or full RSS reader taxonomy.
- No permanent archive guarantee for unsaved feed items.
- No new external storage dependency.

## Decisions

### 1. Store saved states on `LinkFeedItem`

Add boolean fields to the cached feed item model:

```text
favorite
readLater
```

Missing values from older Nitrite documents default to `false`. Feed refresh upsert logic
MUST merge existing `read`, `favorite`, and `readLater` values into the refreshed item before
writing it back.

**Rationale**: This matches the existing read-state model and keeps the implementation small.

**Alternative considered**: Store item states in a separate collection keyed by item ID. That
would be useful for per-user state later, but it creates extra joins and migration work for a
personal-blog oriented plugin.

### 2. Treat saved states as site-level state

Favorite, read-later, and read/unread state apply to the site, not to individual Console users.

**Rationale**: The plugin's main audience is personal blog owners. Site-level state is easier
to reason about and consistent with the current read-state behavior.

**Alternative considered**: Add owner/user fields and require every query/update to scope by
the authenticated principal. This is intentionally deferred unless real multi-user usage
appears.

### 3. Expose explicit state update endpoints

Extend the Console feed API with item state updates such as:

```text
POST /apis/console.api.link.halo.run/v1alpha1/rss/items/{id}/favorite?favorite=true
POST /apis/console.api.link.halo.run/v1alpha1/rss/items/{id}/read-later?readLater=true
```

Existing read-state APIs remain unchanged. Listing accepts optional `favorite` and `readLater`
filters, in addition to existing link, group, read, and cursor parameters.

**Rationale**: Dedicated endpoints keep generated client methods obvious and avoid an
ambiguous generic patch payload for a tiny state surface.

**Alternative considered**: Replace read/favorite/read-later with one generic item-state patch
endpoint. That is more flexible, but would be a larger API shape change than this feature
needs.

### 4. Read-later is cleared when the article is opened

When the Console user opens an item URL, the frontend should mark the item as read and clear
`readLater`. Favorite MUST remain unchanged.

**Rationale**: Opening an article is the strongest signal that the item is no longer queued
for later reading, while favorite is a long-term saved state.

**Alternative considered**: Keep read-later until the user manually clears it. This is more
conservative, but leaves more stale items in the later list.

### 5. Retention skips favorite and read-later items

Age-based, per-link-count, and global-count retention cleanup MUST delete only unsaved items:

```text
favorite != true AND readLater != true
```

Saved items can cause total cached records to exceed the configured normal retention limits.

**Rationale**: Favorite and read-later are explicit user choices. Deleting those items during
normal cache cleanup would violate user expectations.

**Alternative considered**: Keep favorite items only, but allow read-later items to expire.
That is simpler for disk usage, but "read later" still represents explicit intent and should
not vanish silently.

## Risks / Trade-offs

- **[Risk] Saved items can grow beyond normal retention limits** -> **Mitigation**: Keep the
  scope to explicit user-saved items and leave future bulk cleanup controls as a separate
  change if real usage needs it.
- **[Risk] Filtering by several booleans can make Nitrite queries less efficient** ->
  **Mitigation**: Add non-unique indexes for the new boolean fields, matching the existing
  `read` index.
- **[Risk] Upsert refreshes could accidentally reset saved states** -> **Mitigation**: Extend
  store tests to verify `read`, `favorite`, and `readLater` are preserved across duplicate
  item upserts.
- **[Risk] Clearing read-later on external-link click can fail after the browser opens the
  URL** -> **Mitigation**: Update local UI state optimistically only after API calls complete
  where possible, and keep the action idempotent so reloads recover from partial failures.

## Migration Plan

1. Add `favorite` and `readLater` fields to feed item model/query/OpenAPI output.
2. On Nitrite store startup, add indexes and backfill missing saved-state fields to `false`.
3. Update upsert merge behavior so existing saved states survive refreshed feed entries.
4. Update retention delete filters so only unsaved items are eligible for cleanup.
5. Regenerate OpenAPI docs and the generated TypeScript client.
6. Update Console UI to expose saved-state actions and filters.

Rollback is safe for data correctness: older code ignores the extra Nitrite fields, and the
existing read-state and feed item data remain usable.

## Open Questions

- None for the initial proposal.
