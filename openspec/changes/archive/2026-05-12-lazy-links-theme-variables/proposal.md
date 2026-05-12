## Why

The current `/links` theme route only exposes a single `groups` variable containing fully-aggregated group+link data. Theme authors cannot access a flat link list or a lightweight group list independently. Additionally, all model variables are resolved eagerly during rendering, causing expensive N+1 queries (via `groupBy()`) even when the template only needs a subset of the data. Plugin-photos solves this with `LazyContextVariable`, and we should adopt the same pattern for consistency and performance.

## What Changes

- Add `simpleGroups` variable to `/links` route — a lightweight list of groups without nested links.
- Add `links` variable to `/links` route — a flat list of all links, filterable via `?group=` query parameter.
- Convert `groups`, `simpleGroups`, and `links` to `LazyContextVariable` so each is queried only when the template actually references it.
- Add `group` query parameter support on `/links` to filter the `links` variable by group name.
- Expose the current `group` query value in the model for template use.
- Add `_templateId` to the model for Halo theme system compatibility.

## Capabilities

### New Capabilities
- `lazy-theme-variables`: On-demand template variable loading for the `/links` theme route using Thymeleaf's `LazyContextVariable`.

### Modified Capabilities
- (none — this is an implementation-level enhancement; the existing `groups` contract remains unchanged)

## Impact

- **Backend**: `LinkRouter.java` — handler refactoring to create lazy variables and read query parameters.
- **Theme API**: New template variables available: `simpleGroups`, `links`, `group`.
- **Backward compatibility**: Existing `groups` variable is preserved with the same type and semantics.
