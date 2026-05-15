## Why

The `role-template-link-manage` role in `roleTemplate.yaml` currently uses `nonResourceURLs` for the `/-/sort` and `/-/detail` subresource endpoints. In Halo's RBAC system, these should be declared as `resources` with `resourceNames: ["-"]` to align with how subresources are authorized throughout the core platform (e.g., `users/avatar`, `plugins/bundle.js`).

## What Changes

- Fix RBAC rules in `roleTemplate.yaml` for `role-template-link-manage`:
  - Replace `nonResourceURLs` entries for `links/-/sort`, `link-groups/-/sort`, and `links/-/detail` with proper `resources` + `resourceNames: ["-"]` entries.
  - This is a **BREAKING** change for any external role bindings that relied on the old `nonResourceURLs` paths, but fixes authorization consistency with Halo core patterns.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This is a fix to existing RBAC configuration, not a change to plugin capabilities.

## Impact

- `src/main/resources/extensions/roleTemplate.yaml` — RBAC template only.
- No frontend, backend Java code, or API contract changes.
