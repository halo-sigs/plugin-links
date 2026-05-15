## Why

The anonymous role template (`role-template-link-anonymous`) configures `resources: ["link-random", "link-count"]` for the public endpoints `/links/-/random` and `/links/-/count`. In Halo's RBAC model (based on Kubernetes RBAC), these resource names do not match the actual API paths, causing anonymous requests to these endpoints to receive `403 Forbidden` instead of `200 OK`.

Halo's RBAC matches `/-/subresource` paths using the `resource/subresource` syntax in the `resources` field (e.g., `links/random` matches `/links/-/random`).

## What Changes

- Fix `role-template-link-anonymous` in `roleTemplate.yaml` to use `resources: ["links/random", "links/count"]` for the `/-/random` and `/-/count` endpoints
- No backend or frontend code changes

## Capabilities

### New Capabilities
- *(none)*

### Modified Capabilities
- *(none — this is a configuration fix without changing spec-level behavior)*

## Impact

- **Configuration only**: `src/main/resources/extensions/roleTemplate.yaml`
- **No API contract changes**
- **No frontend changes**
