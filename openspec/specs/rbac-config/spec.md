# rbac-config Specification

## Purpose
Define RBAC access for public link endpoints and delegated LinkApplication management operations.

## Requirements

### Requirement: Anonymous users can access public subresource endpoints
The system SHALL allow unauthenticated users to access the `/links/-/random` and `/links/-/count` public endpoints through correct RBAC configuration using the subresource syntax (`links/random`, `links/count`).

#### Scenario: Anonymous access to link count succeeds
- **WHEN** an unauthenticated user requests `GET /apis/api.link.halo.run/v1alpha1/links/-/count`
- **THEN** the system returns `200 OK` with the link count

#### Scenario: Anonymous access to random links succeeds
- **WHEN** an unauthenticated user requests `GET /apis/api.link.halo.run/v1alpha1/links/-/random?maxSize=5`
- **THEN** the system returns `200 OK` with a list of random links

### Requirement: Link managers can operate application subresources
The system SHALL authorize delegated link managers for LinkApplication named and collection
subresources according to Halo's parsed resource and resource-name semantics.

#### Scenario: Manager approves a named application
- **WHEN** a user with the link-management role creates
  `linkapplications/{application-name}/approve`
- **THEN** RBAC matches resource `linkapplications/approve` with the actual application name
- **AND** does not require the resource name to equal `-`

#### Scenario: Manager rejects a named application
- **WHEN** a user with the link-management role creates
  `linkapplications/{application-name}/reject`
- **THEN** RBAC authorizes the named application subresource

#### Scenario: Manager verifies a named application
- **WHEN** a user with the link-management role creates
  `linkapplications/{application-name}/verify`
- **THEN** RBAC authorizes the named application subresource

#### Scenario: Manager reads application-scoped Comment context
- **WHEN** a user with the link-management role gets
  `linkapplications/{application-name}/origin-comment`
- **THEN** RBAC authorizes that application subresource
- **AND** does not grant the role general get or list permission on Halo Comment resources

#### Scenario: Manager cleans a filtered application collection
- **WHEN** a user with the link-management role creates the collection cleanup subresource at
  `linkapplications/-/cleanup`
- **THEN** RBAC authorizes resource `linkapplications/cleanup` with resource name `-`

#### Scenario: Viewer attempts a management operation
- **WHEN** a user has only the link-view role
- **THEN** approve, reject, verify, origin-Comment, and cleanup operations remain forbidden

### Requirement: Source Comment mutations retain Halo Comment permissions
The system SHALL require Halo's existing Comment-management permission for source-Comment approval
and reply actions and SHALL NOT grant that authority through plugin-links' link-management role.

#### Scenario: Reviewer has both permissions
- **WHEN** a reviewer has `plugin:links:manage` and `system:comments:manage`
- **THEN** Console enables the source-Comment actions for an eligible Comment-origin application
- **AND** Halo authorizes the Comment and reply mutations through its existing APIs

#### Scenario: Link manager lacks Comment management
- **WHEN** a reviewer has `plugin:links:manage` without `system:comments:manage`
- **THEN** Console leaves the source preview and general Comment-management link available
- **AND** displays the source-Comment controls as disabled with a permission explanation
- **AND** does not attempt a Comment or reply mutation

#### Scenario: Link role is installed or reconciled
- **WHEN** plugin-links installs or reconciles its link-management RoleTemplate
- **THEN** the role does not depend on, aggregate, or reproduce the Halo Comment-management role

#### Scenario: Permission changes before Comment mutation
- **WHEN** a reviewer loses Comment-management permission after opening the application
- **THEN** Halo rejects the Comment mutation
- **AND** Console reports a Comment-only failure without reverting or misreporting successful link
  approval
