## Why

The friend-link feed page lets users mark individual RSS/Atom items as read, but catching up
after scanning a page still requires toggling every unread item one by one. A scoped
"mark all as read" action makes the reader workflow faster while preserving the current
cursor-based list model.

## What Changes

- Add a Console action on the friend-link feed page to mark all currently loaded unread feed
  items as read.
- Show a confirmation dialog before applying the action, including the number of unread items
  that will be affected.
- Apply the action only to the current loaded list data for the active subscription and
  read-state filters; older items that have not been loaded through pagination are not changed.
- Reload the active feed item list after the action completes so the current read filter and
  item badges reflect persisted state.
- Disable or no-op the action when the current loaded list has no unread items.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `link-rss-feed`: Add Console-level requirements for marking the currently loaded feed list
  as read from the friend-link feed page.

## Impact

- Frontend: `console/src/views/LinkFeedList.vue`, feed item action/composable code, generated
  feed API client usage, dialog/loading/toast states.
- Backend/API: no required contract change for the scoped current-loaded-list behavior; reuse
  the existing single-item mark-read endpoint.
- OpenAPI/client generation: not expected unless implementation expands the scope to
  server-side batch updates.
- Tests: frontend type-check/lint and browser smoke for confirmation, disabled state, and
  successful current-list mark-read behavior.
