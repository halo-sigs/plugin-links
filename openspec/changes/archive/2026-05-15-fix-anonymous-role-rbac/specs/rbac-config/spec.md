## ADDED Requirements

### Requirement: Anonymous users can access public subresource endpoints
The system SHALL allow unauthenticated users to access the `/links/-/random` and `/links/-/count` public endpoints through correct RBAC configuration using the subresource syntax (`links/random`, `links/count`).

#### Scenario: Anonymous access to link count succeeds
- **WHEN** an unauthenticated user requests `GET /apis/api.link.halo.run/v1alpha1/links/-/count`
- **THEN** the system returns `200 OK` with the link count

#### Scenario: Anonymous access to random links succeeds
- **WHEN** an unauthenticated user requests `GET /apis/api.link.halo.run/v1alpha1/links/-/random?maxSize=5`
- **THEN** the system returns `200 OK` with a list of random links
