## Why

The current RSS updates view exposes favorite and read-later as ordinary list filters, but
users treat them as separate saved-item workflows rather than ways to narrow the main feed.
This makes the primary filter bar heavier and hides the two most valuable saved-item
surfaces behind dropdowns.

## What Changes

- Remove favorite-state and read-later-state controls from the main RSS updates filter bar.
- Add a persistent favorite entry in the RSS updates page header that opens a saved favorites
  list without leaving the page.
- Add a read-later area near the top of the RSS updates page so pending items are visible at
  a glance before the normal feed list.
- Keep the existing per-item favorite and read-later toggle controls in the feed item list.
- Reuse the existing feed item listing API saved-state query parameters for the favorites
  list and read-later area.
- Preserve the behavior where opening a read-later item marks it as read and removes it from
  read-later while keeping favorite state.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `link-rss-feed`: refine Console saved-item workflows for favorite and read-later feed
  items.

## Impact

- Frontend: `LinkFeedList.vue` layout and saved-item UI state management in
  `use-link-feed.ts` or adjacent composables.
- Backend/API: no required contract changes; existing `favorite` and `readLater` list query
  parameters and toggle endpoints should be reused.
- OpenAPI/client generation: not expected unless implementation discovers an API contract gap.
- Tests: frontend type-check/lint, Gradle build/test, and browser smoke/E2E for the revised
  saved-item workflows.
