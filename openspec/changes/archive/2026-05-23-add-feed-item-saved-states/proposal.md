## Why

The RSS updates view can already collect friend-link articles, but users still need a way
to keep important items and defer items they want to read later. This plugin is mainly used
on personal blogs, so these saved states can be simple site-level item states rather than
per-user timelines.

## What Changes

- Add site-level favorite and read-later states to cached RSS/Atom feed items.
- Allow Console users to toggle favorite and read-later states from the friend-link updates view.
- Allow feed item listing to filter by favorite and read-later state alongside the existing
  link, group, and read-state filters.
- Preserve favorite and read-later states when the same feed item is refreshed again.
- Exclude favorite or read-later items from RSS cache retention cleanup so saved items are
  not silently deleted.
- When a user opens an item from the read-later list, mark it read and remove it from
  read-later while leaving favorite unchanged.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `link-rss-feed`: Extend cached feed item state, Console listing filters, item state update
  APIs, and retention behavior for favorite and read-later workflows.

## Impact

- Backend RSS item model, query model, Nitrite store, retention cleanup, Console feed endpoint,
  RBAC role templates, OpenAPI docs, and tests.
- Frontend generated API client, feed item composable, and `LinkFeedList.vue` Console view.
- No breaking API or storage changes are expected; existing cached items should default the new
  saved-state fields to `false`.
