## Why

The current RSS updates view behaves like a management form: link, group, and read-state filters sit above the feed, while the article list is visually secondary. A two-column reader layout will make subscribed sources easier to scan and keep the primary reading workflow focused on source selection, read-state tabs, and article browsing.

## What Changes

- Redesign the Console RSS updates view into a responsive two-column layout.
- Move subscribed-link selection into a left-side subscription list with an "all updates" entry.
- Replace the read-state dropdown with visible tabs for all, unread, and read items above the article list.
- Remove group filtering from the RSS updates UI.
- Keep article item cards, cursor pagination, read/unread toggles, favorite toggles, and read-later toggles working within the new layout.
- Preserve backend API compatibility; no RSS feed item query endpoints or persistence contracts are changed.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `link-rss-feed`: Update the Console RSS updates view requirements so the primary browsing workflow uses a subscription sidebar plus read-state tabs, and no longer exposes group filtering in the UI.

## Impact

- Frontend: `console/src/views/LinkFeedList.vue`, RSS feed composables, and likely small RSS-specific presentational components.
- Specs: `openspec/specs/link-rss-feed/spec.md` will receive a delta for the updated Console RSS updates view requirements.
- Backend/API: no expected changes.
- Dependencies: no expected changes.
