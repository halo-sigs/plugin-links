## Context

The plugin currently supports single-link creation via `LinkCreationModal.vue` and bulk operations (select + delete/move) via `LinksCard.vue`. There is no way for users to import multiple links from an external source (e.g., a spreadsheet or another CMS export).

## Goals / Non-Goals

**Goals:**
- Provide a modal-based UI for bulk importing links from pasted text.
- Support a simple `|` delimited format: `URL|displayName|description|logo`.
- Integrate with the existing `getLinkDetail` console endpoint for optional metadata scraping.
- Allow users to preview and edit parsed results before committing the import.
- Reuse existing frontend patterns (Vue Query mutations, chunking, modal composition).

**Non-Goals:**
- No backend changes (no new endpoints, no extension model changes).
- No CSV/JSON/Excel file upload (text-only paste for simplicity).
- No batch creation endpoint on the backend (frontend loops over existing `createLink`).

## Decisions

### 1. Two-step modal flow (paste → preview → import)
**Rationale**: Online scraping may produce inaccurate titles or fail for some URLs. A preview step lets users review and edit before creating links. This matches common import UX patterns.

### 2. Target group selected via dropdown
**Rationale**: The user explicitly requested selecting from existing groups rather than creating groups during import. A single dropdown is simpler than per-link group assignment. All imported links share the same `spec.groupName`.

### 3. Format: `URL|displayName|description|logo`
**Rationale**: `|` is rarely used in URLs or descriptions, making it a pragmatic delimiter. All fields after URL are optional. Empty segments are treated as omitted.

### 4. Online scraping on the frontend with concurrency limit
**Rationale**: Reuses the existing `getLinkDetail` endpoint. A concurrency limit of 3 prevents overwhelming the server or triggering rate limits. Users are warned that scraping may take time.

### 5. Priority auto-assignment
**Rationale**: Reuses the existing logic from `LinkCreationModal.vue`: query the current max priority in the target group, then assign `max + 1`, `max + 2`, etc.

### 6. Error handling: per-row status
**Rationale**: Some URLs may fail scraping or have invalid formats. These are shown in the preview with error indicators. Users can fix or uncheck them before import.

## Risks / Trade-offs

- **[Risk]** Large imports (100+ links) will take time due to frontend chunking and scraping.
  → **Mitigation**: Concurrency limit, progress indication, and a warning message when online scraping is enabled.
- **[Risk]** `|` delimiter may conflict with user data (e.g., description contains `|`).
  → **Mitigation**: Document the format clearly. Acceptable trade-off for simplicity; users can edit descriptions after import.
- **[Risk]** `getLinkDetail` may fail or return poor metadata for some sites.
  → **Mitigation**: Preview step lets users edit scraped values. Failed scrapes show as errors with manual fallback.

## Migration Plan

1. Create `LinkImportModal.vue` with paste UI and group selector.
2. Add parse logic to split textarea lines by `|`.
3. Integrate `getLinkDetail` calls with concurrency limit for online scraping.
4. Build preview table with inline editing.
5. Implement batch create using chunked `createLink` calls.
6. Add import button to `LinkList.vue`.
7. Verify with `./gradlew build` and `pnpm type-check`.
