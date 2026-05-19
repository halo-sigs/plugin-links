## Why

Currently users can only add links one by one through the `LinkCreationModal`. When migrating from another platform or setting up a new site, manually creating tens or hundreds of links is tedious. A bulk import feature would significantly improve the onboarding and migration experience.

## What Changes

- Add a new `LinkImportModal.vue` component that provides a two-step bulk import flow.
- Add an import button to `LinkList.vue` (or reuse the existing dropdown in `LinksCard.vue`).
- The modal supports:
  - Selecting a target group from existing `LinkGroup`s.
  - Pasting multiple links in a textarea (one per line, `|` delimited).
  - Optional online metadata scraping (title, description, favicon) via the existing `getLinkDetail` endpoint.
  - Preview and edit parsed results before confirming import.
- Batch create links on the frontend using the existing `linksCoreApiClient.link.createLink` API, following the existing chunk pattern from `LinksCard.vue`.

## Capabilities

### New Capabilities

- `bulk-link-import`: Console UI for importing multiple links from pasted text with optional metadata scraping.

### Modified Capabilities

- (none — no backend API changes, reuses existing endpoints)

## Impact

- **Backend**: No changes. Reuses existing `createLink` (core extension API) and `getLinkDetail` (console API).
- **Frontend**: New `LinkImportModal.vue`, minor addition to `LinkList.vue` or `LinksCard.vue` to open the modal.
