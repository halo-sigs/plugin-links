## Context

The public API endpoint `GET /apis/api.link.halo.run/v1alpha1/linkgroups` currently returns `ListResult<LinkGroupVo>` (paginated). The backend implementation (`LinkPublicQueryServiceImpl.listGroups`) already fetches all `LinkGroup` extensions into memory via `client.listAll()` and then applies in-memory pagination with `ListResult.subList`. For a dataset that rarely exceeds a few dozen items, this pagination is unnecessary overhead.

The console frontend (`use-group-fetch.ts`, `GroupSortModal.vue`) compensates for this by calling `paginate()` with `size: 1000`, effectively bypassing pagination client-side.

## Goals / Non-Goals

**Goals:**
- Simplify the public linkgroups endpoint to return a flat list.
- Remove the workaround (`paginate()` with large page size) from the console frontend.
- Ensure the response preserves the existing sort order (by priority desc, then creation timestamp desc, then name asc).

**Non-Goals:**
- Changing the `LinkGroupVo` schema or fields.
- Adding a console-side query endpoint (console continues to use the public API).
- Modifying the `Link` endpoint or its pagination behavior.
- Backward compatibility for the endpoint (not yet released).

## Decisions

**Decision: Flatten the response directly in `LinkGroupQueryEndpoint`**
- Rationale: The endpoint is a thin routing layer. Changing the response type here keeps the change localized. The existing `listGroups` method in `LinkPublicQueryServiceImpl` can be adapted by simply skipping the `ListResult.subList` step.
- Alternative considered: Adding a separate `/linkgroups/-/all` endpoint. Rejected because it introduces endpoint duplication for a use case that should always return all items.

**Decision: Keep sorting logic unchanged**
- Rationale: Frontend depends on the current ordering (priority desc, then creation timestamp desc, then name asc). The `groupComparator()` logic stays intact.

**Decision: Frontend switches from `paginate()` to direct API call**
- Rationale: `paginate()` is a utility for handling paginated Halo APIs. With a flat list response, it's unnecessary and would fail to compile against the regenerated client.

## Risks / Trade-offs

- **[Risk] Breaking change for consumers of the public API** → **Mitigation**: The endpoint is not yet released externally; only internal console code consumes it.
- **[Risk] Regenerated OpenAPI client may break existing imports** → **Mitigation**: After backend build, regenerate the client (`./gradlew generateApiClient`), then update frontend call sites.
