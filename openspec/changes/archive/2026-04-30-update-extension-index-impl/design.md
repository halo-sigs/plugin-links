## Context

The plugin currently declares extension indexes in `LinkPlugin.start()` using the deprecated `IndexAttributeFactory.simpleAttribute()` and `new IndexSpec()` APIs. Query construction in `LinkRouter` and `LinkFinderImpl` uses the deprecated `QueryFactory` static helpers (`and`, `or`, `contains`, `equal`, `isNull`).

Halo 2.22.0 introduced a cleaner index/query API:
- `IndexSpecs.single(name, keyType)` and `IndexSpecs.multi(name, keyType)` replace `IndexAttributeFactory` + `IndexSpec` constructors.
- `Queries` utility replaces `QueryFactory` for building `FieldSelector` conditions.
- Index values are no longer limited to `String`; `Integer`, `Long`, `Boolean`, and `Instant` are supported.

The plugin targets Halo `2.22.5` (see `build.gradle` and `plugin.yaml`), so the new APIs are available.

## Goals / Non-Goals

**Goals:**
- Replace all deprecated index declaration APIs with `IndexSpecs.single()`.
- Replace all deprecated query construction APIs with `Queries`.
- Use typed index keys where beneficial (e.g., `spec.priority` as `Integer` rather than coercing to `String`).
- Ensure the plugin compiles cleanly after migration.

**Non-Goals:**
- No functional changes to link/group management behavior.
- No new features (e.g., new query methods like `listAllNames`).
- No frontend changes.
- No changes to the OpenAPI spec or console API client.

## Decisions

**1. Use `IndexSpecs.single()` for all indexes**
- All current indexes are single-value attributes (`displayName`, `description`, `url`, `groupName`, `priority`). None are multi-value, so `IndexSpecs.multi()` is unnecessary.
- Rationale: Simplest migration path; matches current semantics.

**2. Keep `String` for `groupName` and text fields, migrate `priority` to `Integer`**
- `spec.priority` is natively an `Integer` in the model. The old code coerced it to `String` via `String.valueOf()`. The new API allows indexing as `Integer.class` directly.
- Rationale: Eliminates unnecessary string coercion and aligns the index type with the model type.

**3. Use `Queries` static methods with method references**
- `QueryFactory.and(...)` becomes `Queries.and(...)`; `QueryFactory.isNull(...)` becomes `Queries.isNull(...)`; etc.
- Rationale: Direct API replacement with minimal code churn.

## Risks / Trade-offs

- **[Risk]** Halo platform version mismatch → If the Halo platform dependency in `build.gradle` does not actually expose the new APIs at compile time, the build will fail.  
  **Mitigation**: The plugin already depends on Halo `2.22.5`, which includes the new APIs. A `./gradlew build` after migration will surface any API drift immediately.

- **[Risk]** Typed index for `priority` could change sort behavior → The old index stored priority as strings (e.g., `"10"`), while `Integer` indexes sort numerically.  
  **Mitigation**: Numeric sorting is the correct behavior for `priority`; string sorting of numeric values was a latent bug.
