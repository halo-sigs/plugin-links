## ADDED Requirements

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
