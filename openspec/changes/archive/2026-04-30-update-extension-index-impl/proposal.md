## Why

Halo 2.22.0 refactored the custom model index and query APIs. The legacy APIs used in this plugin—`IndexAttributeFactory.simpleAttribute()` / `new IndexSpec()` and `QueryFactory`—are now deprecated. Migrating to the new `IndexSpecs` / `Queries` APIs ensures forward compatibility and access to richer index types (`Integer`, `Long`, `Boolean`, `Instant`) and new query helpers.

## What Changes

- **Migrate index declarations** in `LinkPlugin.start()` from `IndexAttributeFactory.simpleAttribute()` / `new IndexSpec()` to `IndexSpecs.single(name, keyType)`.
- **Migrate query construction** in `LinkRouter` and `LinkFinderImpl` from `QueryFactory.and/or/contains/equal/isNull` to `Queries` creating `FieldSelector`.
- **Adopt typed index keys** where appropriate (e.g., `spec.priority` as `Integer` instead of string).
- **No functional changes** to the plugin's behavior, APIs, or frontend.

## Capabilities

### New Capabilities

*None—this is a compatibility migration with no new user-facing capabilities.*

### Modified Capabilities

*None—spec-level behavior of link and group management does not change.*

## Impact

- Backend Java code only: `LinkPlugin.java`, `LinkRouter.java`, `LinkFinderImpl.java`.
- No frontend, API schema, or database migration impact.
