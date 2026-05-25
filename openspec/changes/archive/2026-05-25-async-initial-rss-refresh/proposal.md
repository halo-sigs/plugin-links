## Why

Creating or editing a link currently waits for the initial RSS refresh to finish before the
modal closes. Remote feed fetching can be slow or unreliable, so a successful link save should
not feel blocked by RSS content retrieval.

## What Changes

- Change the create/edit link flow so the initial RSS refresh is started after a successful save
  without delaying modal close or saved-link cache invalidation.
- Keep link save success feedback separate from RSS refresh progress or failure feedback.
- Preserve the existing manual refresh behavior on the RSS feed page; those actions still wait
  for remote refresh before reloading the visible feed item list.
- No breaking API or data model changes.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `link-rss-feed`: Initial RSS refresh after creating or enabling RSS for a link becomes
  non-blocking from the Console save-modal perspective.

## Impact

- Frontend: `LinkCreationModal.vue` and `LinkEditingModal.vue` save success flow.
- Frontend cache behavior: link/group and RSS subscription queries should still be invalidated
  promptly after save, with RSS-related data refreshed again when the background fetch settles.
- Backend APIs and stored RSS state are unchanged.
