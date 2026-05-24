## Context

The Console friend-link feed page already loads the primary RSS/Atom item list through
`useLinkFeedItems()`, which combines cursor-paginated pages into the currently loaded
`items` array. Individual cards can mark one item as read or unread through the generated
`markLinkFeedItemRead` API method, and the backend persists read state in the embedded feed
item store.

There is no backend batch mark-read endpoint today. The requested behavior is scoped to
"current list data", which maps naturally to the frontend's currently loaded primary list
rather than every matching row in the embedded store.

## Goals / Non-Goals

**Goals:**

- Add a clear "mark all as read" action to the primary friend-link feed toolbar.
- Confirm before mutating item state.
- Mark only currently loaded unread items in the active primary list as read.
- Preserve active subscription and read-state filters after completion.
- Keep request failures visible without adding duplicate HTTP failure toasts beyond existing
  global error handling.

**Non-Goals:**

- Do not add server-side batch read-state APIs for this scoped workflow.
- Do not mark unread items that have not been loaded through cursor pagination.
- Do not add the action to the favorites or read-later modals unless requested separately.
- Do not change per-item read/unread behavior, saved states, retention, or RSS refresh flows.

## Decisions

### Reuse the single-item mark-read API

The action will collect unread IDs from `mainFeed.items` and call the existing generated
`markLinkFeedItemRead({ id, read: true })` method for those IDs.

Rationale:
- It matches the "current loaded list" scope without new backend contract or generated-client
  churn.
- Existing backend behavior already persists read state and returns not-found for missing
  item IDs.
- The likely affected count is bounded by loaded pages, not by the entire embedded store.

Alternative considered: add `POST rss/items/-/read` with query filters. That would support
true server-side "all matching items" behavior, but it changes the API contract and conflicts
with the narrower current-list wording.

### Put the action in the primary feed toolbar

Place the action alongside the existing current/all remote refresh and cache reload controls.
Disable it when the currently loaded primary list contains no unread items or when another
list-level request is already in flight.

Rationale:
- The action applies to the visible primary article stream, so the toolbar is the closest
  control surface.
- The disabled state prevents an empty confirmation dialog.
- Keeping it out of the page header avoids confusing it with saved-item collection actions.

### Confirm with an explicit affected count

Use the existing `Dialog.warning` pattern before applying the action. The dialog should include
the number of loaded unread items and explain that only currently loaded items will be changed.
When pagination still has more items available, the wording should make the loaded-item scope
especially clear.

Rationale:
- Bulk read-state changes are reversible per item but still easy to trigger accidentally.
- Counted confirmation helps users understand exactly what will change.

### Batch requests with bounded concurrency

Issue mark-read requests in small chunks instead of firing an unbounded `Promise.all` across all
loaded pages. After all chunks finish, reload the active feed list and show one success summary.

Rationale:
- Reuses the existing API safely even if the user has auto-loaded several pages.
- One summary toast is less noisy than per-item feedback.

## Risks / Trade-offs

- [Risk] Active `unread` filter can show older unread items after reload because only loaded
  items were marked. -> Mitigation: confirmation and success text say "currently loaded" and
  include the affected count.
- [Risk] Some item updates can fail after others succeed. -> Mitigation: let existing HTTP
  interception surface failures and reload the list after the attempted batch so persisted state
  is visible.
- [Risk] A large loaded list can produce many requests. -> Mitigation: bounded chunking keeps
  request bursts small; a backend batch endpoint can be proposed later if this becomes a real
  performance bottleneck.
