## Why

The current backend routing is inconsistent: console APIs are split between the legacy plugin path (`/apis/api.plugin.halo.run/...`) and proper CustomEndpoints, while `LinkRouter.java` mixes theme routing, REST endpoints, and a query class all in one file. This makes the codebase hard to navigate and deviates from the pattern established in `plugin-photos`.

## What Changes

- **BREAKING**: Remove the legacy plugin REST API from `LinkRouter` (`/apis/api.plugin.halo.run/v1alpha1/plugins/PluginLinks/**`). All console endpoints move to `LinkEndpoint` under `/apis/console.api.link.halo.run/v1alpha1/**`.
- **BREAKING**: Extract the `LinkQuery` nested class from `LinkRouter` into a standalone `LinkQuery.java`.
- Split `LinkRouter.java` into a focused theme router (only `/links` Thymeleaf route).
- Create `LinkEndpoint.java` as a `CustomEndpoint` to host all console APIs: list links, link detail, sort links, sort link groups.
- Update `roleTemplate.yaml` to reference the new `console.api.link.halo.run` group instead of `api.plugin.halo.run`.
- Update `build.gradle` OpenAPI grouping to drop the old plugin path.
- Regenerate the TypeScript API client and update console frontend imports accordingly.

## Capabilities

### New Capabilities

- `console-link-api`: Console-side custom endpoints for link/group management (list, detail, sort).

### Modified Capabilities

- (none — no spec-level behavior changes, only structural refactoring)

## Impact

- **Backend**: `LinkRouter.java`, new `LinkEndpoint.java`, new `LinkQuery.java`, `LinkQueryEndpoint.java`, `roleTemplate.yaml`, `build.gradle`.
- **Frontend**: `console/src/api/index.ts` and all Vue/TS files referencing the old `ApiPluginHaloRunV1alpha1LinkApi` client.
- **API consumers**: Any external callers using `/apis/api.plugin.halo.run/v1alpha1/plugins/PluginLinks/**` will need to migrate to `/apis/console.api.link.halo.run/v1alpha1/**`.
