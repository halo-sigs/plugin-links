## Why

Public RSS aggregation currently exposes every cached feed item, while read and saved states only
support Console reading workflows. Site owners need a durable way to hide selected RSS items from
normal Console and public feed results without having those items reappear after the next refresh.

## What Changes

- Add a reversible, site-level hidden state to cached RSS/Atom feed items, scoped to the selected
  stable item IDs rather than inferred URL or content matches.
- Exclude hidden items from normal Console lists, saved-item lists, unread counts, the public REST
  feed resource, and the theme Finder while keeping them in physical cache counts.
- Add Console operations for single-item and explicit-ID batch hide/unhide, plus an accurate hidden
  item count and a dedicated "已隐藏" list for restoring items.
- Preserve hidden, read, favorite, and read-later states across RSS refreshes; protect hidden items
  from normal retention cleanup and remove them only when explicitly unhidden or their owning Link
  is deleted.
- Add an idempotent SQLite startup compatibility step that appends the hidden column to existing
  databases when absent without changing the current schema version.
- Add selection-mode Console interactions for the currently loaded items and refresh all affected
  Vue Query data after successful mutations.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `link-rss-feed`: Extend cached item state, persistence compatibility, query visibility, retention,
  Console item management, anonymous feed results, Finder results, and count semantics with
  reversible RSS item hiding.

## Impact

- Backend feed item model/query/store, SQLite schema initialization, retention and unread queries,
  Console feed endpoint, public query service, Finder integration, OpenAPI output, and tests.
- Frontend generated API client, Vue Query composables, feed item cards/lists/modals, selection
  state, confirmation flows, count display, and Console tests.
- No new dependency, plugin setting, public hidden-item field, automatic content matching, or
  breaking public API is introduced.
