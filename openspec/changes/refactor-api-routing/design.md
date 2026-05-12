## Context

Currently `LinkRouter.java` holds three responsibilities:
1. Theme route registration (`/links` → Thymeleaf template).
2. Legacy plugin REST API under `/apis/api.plugin.halo.run/v1alpha1/plugins/PluginLinks/**` (list, detail, sort endpoints).
3. The `LinkQuery` nested query class, which is referenced externally by `LinkQueryEndpoint` via `new LinkRouter.LinkQuery(...)`.

`plugin-photos` provides a clean reference architecture where theme routing, console endpoints, and public endpoints each live in dedicated classes (`PhotoRouter`, `PhotoEndpoint`, `PhotoQueryEndpoint`). `plugin-links` should follow the same pattern.

## Goals / Non-Goals

**Goals:**
- Move all console APIs to `CustomEndpoint` classes under `console.api.link.halo.run/v1alpha1`.
- Extract `LinkQuery` into a standalone top-level class.
- Reduce `LinkRouter` to theme-route-only responsibilities.
- Update RBAC rules in `roleTemplate.yaml` to match the new API group.
- Regenerate the OpenAPI TypeScript client and update all console frontend references.

**Non-Goals:**
- No changes to the public API (`api.link.halo.run/v1alpha1`) behavior or paths.
- No changes to the core extension API (`core.halo.run/v1alpha1/links/**`).
- No new features (sorting logic, query behavior remain identical).

## Decisions

### 1. Create `LinkEndpoint` as a new `CustomEndpoint`
**Rationale**: `LinkGroupEndpoint` already demonstrates the correct pattern for console APIs. Grouping link-related console operations (`list`, `detail`, `sortLinks`, `sortLinkGroups`) into a single `LinkEndpoint` mirrors `PhotoEndpoint` in `plugin-photos`.

### 2. Keep `LinkRouter` name for theme routing
**Rationale**: Renaming to `ThemeRouter` was considered, but `PhotoRouter` in `plugin-photos` keeps the domain prefix (`Photo`). Consistency with the reference project is more valuable than a generic name. The class will simply be stripped of its REST API baggage.

### 3. `LinkQuery` becomes a standalone public class
**Rationale**: `LinkQueryEndpoint` currently reaches into `LinkRouter` with `new LinkRouter.LinkQuery(...)`. This is a coupling smell. Extracting it eliminates the dependency and makes the query reusable.

### 4. `LinkQueryEndpoint.linkCount` will reference the standalone `LinkQuery`
**Rationale**: It currently uses `LinkRouter.LinkQuery`. After extraction, the reference becomes `new LinkQuery(...)`. The behavior stays identical.

### 5. `roleTemplate.yaml` follows `plugin-photos` RBAC pattern
**Rationale**: `plugin-photos` assigns both `core.halo.run` and `console.api.photo.halo.run` to the same role rules. The same approach applies here: view/manage roles get both core and console API groups.

## Risks / Trade-offs

- **[Risk]** Sort endpoints (`POST .../-/sort`) use non-standard REST paths. Halo’s RBAC may not auto-map them to standard verbs.
  → **Mitigation**: Declare them as `nonResourceURLs` in `roleTemplate.yaml` (same pattern as `plugin-photos` upload endpoint).
- **[Risk]** Frontend references the old generated client class name (`ApiPluginHaloRunV1alpha1LinkApi`).
  → **Mitigation**: After backend changes, run `./gradlew generateApiClient`, then update `console/src/api/index.ts` and all call sites. The new class will be `ConsoleApiLinkHaloRunV1alpha1LinkApi`.
- **[Risk]** `build.gradle` OpenAPI grouping currently excludes `/apis/api.link.halo.run/v1alpha1/**`.
  → **Mitigation**: Add it to `pathsToMatch` so the public API client is also generated (for future frontend use or external consumers).

## Migration Plan

1. Implement backend Java changes (new files + deletions + edits).
2. Update `roleTemplate.yaml`.
3. Update `build.gradle` OpenAPI config.
4. Run `./gradlew generateApiClient`.
5. Update console frontend API imports and regenerate.
6. Verify console UI loads and all CRUD/sort operations work.

## Open Questions

- (none)
