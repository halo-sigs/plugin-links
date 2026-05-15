## Context

Halo core platform uses Kubernetes-style RBAC rules. Subresource endpoints such as `users/avatar` or `plugins/bundle.js` are declared under `resources` with `resourceNames: ["-"]` to match the `-` placeholder (collection-level operation). The plugin-links role template currently uses `nonResourceURLs` for `links/-/sort`, `link-groups/-/sort`, and `links/-/detail`, which is inconsistent with core Halo patterns.

## Goals / Non-Goals

**Goals:**
- Align plugin-links RBAC templates with Halo core conventions.
- Ensure authorization checks work correctly for sort and detail endpoints.

**Non-Goals:**
- No changes to backend endpoint definitions or URL paths.
- No changes to frontend code.

## Decisions

- Use `resources: ["links/-/sort", "link-groups/-/sort"]` + `resourceNames: ["-"]` instead of `nonResourceURLs`.
- Use `resources: ["links/-/detail"]` + `resourceNames: ["-"]` instead of `nonResourceURLs`.
- Rationale: matches Halo core role templates (e.g., `users/avatar`, `plugins/bundle.js`).

## Risks / Trade-offs

- **[Risk]** Existing custom roles referencing the old `nonResourceURLs` paths may lose permission.
  - **Mitigation**: This is an internal plugin role template; consumers relying on it should re-sync permissions via Halo's role management UI.
