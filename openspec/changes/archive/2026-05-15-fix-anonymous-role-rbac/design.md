## Context

The plugin exposes public endpoints at `/apis/api.link.halo.run/v1alpha1/links` (list), `/apis/api.link.halo.run/v1alpha1/linkgroups` (list), `/apis/api.link.halo.run/v1alpha1/links/-/random` and `/apis/api.link.halo.run/v1alpha1/links/-/count`.

The anonymous role template currently grants access via `resources: ["links", "linkgroups", "link-random", "link-count"]`. In Halo's RBAC model:
- `resources: ["links"]` correctly matches `/apis/.../links` and `/apis/.../links/{name}`
- `resources: ["link-random"]` does **not** match `/apis/.../links/-/random`
- `resources: ["link-count"]` does **not** match `/apis/.../links/-/count`

Halo's RBAC uses Kubernetes-style resource matching. Subresources like `/-/random` must be matched via the `resource/subresource` syntax (e.g., `links/random`, `links/count`). This is consistent with Halo's attachment role template which uses `resources: ["attachments/upload-from-url"]` to match `/attachments/-/upload-from-url`.

## Goals / Non-Goals

**Goals:**
- Allow anonymous users to access `/-/random` and `/-/count` endpoints
- Keep existing `links` and `linkgroups` list access intact

**Non-Goals:**
- Changing any Java or Vue code
- Adding new endpoints or changing endpoint paths
- Changing authenticated/management role templates

## Decisions

### Decision 1: Use `resources` with subresource syntax
Use `resources: ["links/random", "links/count"]` to match the `/-/random` and `/-/count` endpoints. This follows Halo's established pattern (e.g., `attachments/upload-from-url`) and uses the Kubernetes RBAC subresource matching mechanism where `/-/subresource` in the URL path maps to `resource/subresource` in the role template.

## Risks / Trade-offs

- None significant. This is a straightforward configuration fix following established patterns in Halo.
