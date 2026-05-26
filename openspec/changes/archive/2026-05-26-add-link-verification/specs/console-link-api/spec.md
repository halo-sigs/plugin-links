## ADDED Requirements

### Requirement: Console link verification trigger endpoint
The system SHALL expose a `POST` endpoint at
`/apis/console.api.link.halo.run/v1alpha1/links/-/verification/check` that starts link
verification asynchronously for all links, one group, or selected link names.

#### Scenario: Trigger selected links verification
- **WHEN** a Console user sends a verification request with link names
- **THEN** the system accepts verification work only for those link names
- **AND** the response reports accepted, skipped, and already-running counts

#### Scenario: Trigger group verification
- **WHEN** a Console user sends a verification request with a group name and no link names
- **THEN** the system accepts verification work for links in that group
- **AND** the response reports accepted, skipped, and already-running counts

#### Scenario: Trigger all-links verification
- **WHEN** a Console user sends a verification request without link names or a group name
- **THEN** the system accepts verification work for all links
- **AND** the response reports accepted, skipped, and already-running counts

#### Scenario: Verification trigger returns before checks finish
- **WHEN** a Console user sends a valid verification request
- **THEN** the endpoint responds with HTTP 202 before all remote checks complete

#### Scenario: Verification trigger rejects unknown selected link
- **WHEN** a Console user sends a verification request containing an unknown link name
- **THEN** the endpoint does not enqueue work for the unknown link
- **AND** the response reports that link as skipped
