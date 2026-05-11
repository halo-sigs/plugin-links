## Why

Currently, link and link group reordering is done on the frontend by calling the update API in batches. This approach is unreliable because partial request failures can leave data in an inconsistent or unintended state. A dedicated backend sort endpoint will make reordering atomic and reliable.

## What Changes

- Add a new backend API endpoint `/apis/api.plugin.halo.run/v1alpha1/plugins/PluginLinks/links/-/sort` that accepts a list of link names in the desired order and updates priorities accordingly.
- Add a similar endpoint for link groups at `/apis/api.plugin.halo.run/v1alpha1/plugins/PluginLinks/link-groups/-/sort`.
- Update the frontend (`use-link.ts`, `LinkList.vue`) to call the new sort endpoints instead of sending multiple individual update requests.
- Remove or deprecate the batch-update logic currently used for reordering on the frontend.

## Capabilities

### New Capabilities
- `backend-sort-api`: Atomic reordering of links and link groups via dedicated backend endpoints.

### Modified Capabilities
- *(none)*

## Impact

- **Backend**: `LinkRouter.java` — new route handlers for sort endpoints; possibly a new request DTO.
- **Frontend**: `console/src/composables/use-link.ts`, `console/src/views/LinkList.vue` — replace batch update calls with single sort API call.
- **API**: New plugin REST endpoints. No breaking changes to existing endpoints.
