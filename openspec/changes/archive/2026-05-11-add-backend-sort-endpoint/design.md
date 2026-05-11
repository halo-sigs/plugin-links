## Context

Currently, when a user reorders links or link groups in the Console UI, the frontend sends multiple individual update requests to the existing REST endpoints. Each request updates a single item's `spec.priority` field. If any request fails (e.g., due to a network error or a transient server issue), the final order may be inconsistent with what the user intended. There is no transactional guarantee across these batch requests.

The backend is a Spring WebFlux application using Halo's Extension system. `Link` and `LinkGroup` are extensions with a `priority` field in their spec. The frontend uses Vue Query and an auto-generated OpenAPI client.

## Goals / Non-Goals

**Goals:**
- Provide atomic, server-side reordering for both `Link` and `LinkGroup` entities.
- Ensure the frontend can reorder items with a single API call.
- Maintain compatibility with existing endpoints (no breaking changes).

**Non-Goals:**
- Bulk updates of fields other than `priority`.
- Changing the data model or index definitions.
- Adding transaction-level rollback beyond reactive sequential updates.

## Decisions

**Decision 1: Two separate endpoints (links vs. link-groups)**
- *Rationale*: Matches the existing REST resource structure (`/links` and `/link-groups`). Keeps the API predictable and aligned with current patterns in `LinkRouter.java`.
- *Alternative considered*: A single generic `/sort` endpoint — rejected because it would require passing a resource type discriminator, making the API less RESTful.

**Decision 2: Accept ordered list of names, assign sequential priorities**
- *Rationale*: The frontend drag-and-drop naturally produces an ordered array of item IDs (names). The backend assigns `priority = index` (or `index * step`) to persist the order. This is simple and deterministic.
- *Alternative considered*: Accepting explicit priority values from the frontend — rejected because it leaks ordering logic to the client and is more error-prone.

**Decision 3: Sequential reactive update within a Mono/Flux chain**
- *Rationale*: Halo's `ReactiveExtensionClient` operates on individual extensions. We will fetch each extension by name, update its `spec.priority`, and `update` it, chained reactively. This is not a database transaction, but since it's a single endpoint call, the failure window is much smaller than multiple independent frontend requests.

## Risks / Trade-offs

- **[Risk] Endpoint failure leaves partial updates** → **Mitigation**: While not a true atomic transaction, collapsing N requests into 1 drastically reduces the failure window. The sequential reactive chain ensures updates happen in order.
- **[Risk] Concurrent edits during reordering** → **Mitigation**: This is an existing risk with the current frontend batch approach. The new endpoint does not worsen it.

## Migration Plan

- Implement backend endpoints and DTO.
- Update frontend composable and view to use the new endpoint.
- Remove the old batch-update loop from the frontend.
- Regenerate the OpenAPI client (`./gradlew generateApiClient`) after the backend change.
