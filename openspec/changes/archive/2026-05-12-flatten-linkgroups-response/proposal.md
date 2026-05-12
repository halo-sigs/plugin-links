## Why

The public `/apis/api.link.halo.run/v1alpha1/linkgroups` endpoint currently returns a paginated `ListResult<LinkGroupVo>`. However, link groups are typically small datasets (a handful of categories), and the backend already loads all groups into memory before applying pagination — making the pagination cosmetic and adding unnecessary complexity. The console frontend already works around this by using `size: 1000` with a `paginate()` helper. Removing pagination simplifies both the backend and frontend code.

## What Changes

- **BREAKING**: Change `GET /apis/api.link.halo.run/v1alpha1/linkgroups` response from `ListResult<LinkGroupVo>` to `List<LinkGroupVo>`.
- Update `LinkGroupQueryEndpoint` to return a flat list directly.
- Update `LinkPublicQueryService` and `LinkPublicQueryServiceImpl` to expose a non-paginated `listAllGroups()` method.
- Update console frontend (`use-group-fetch.ts`, `GroupSortModal.vue`) to remove `paginate()` usage and consume the flat list response.
- Regenerate OpenAPI TypeScript client after backend changes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None — this is an API response format change with no new or altered functional requirements.

## Impact

- **Backend**: `LinkGroupQueryEndpoint.java`, `LinkPublicQueryService.java`, `LinkPublicQueryServiceImpl.java`
- **Frontend**: `use-group-fetch.ts`, `GroupSortModal.vue`, generated API client
- **API contract**: Breaking change for the public linkgroups endpoint (acceptable — not yet released)
