## ADDED Requirements

### Requirement: Link verification requests are protected from SSRF
The system SHALL apply URL validation to link verification reachability checks and backlink scan
requests before making any server-side network request.

#### Scenario: Private link URL is blocked during verification
- **WHEN** a verification request targets a link URL resolving to a private or reserved address
- **THEN** the system rejects that fetch before connecting
- **AND** records the reachability verification as failed for that link

#### Scenario: Private backlink scan URL is blocked during verification
- **WHEN** a verification request targets a backlink scan URL resolving to a private or reserved
  address
- **THEN** the system rejects that fetch before connecting
- **AND** records the backlink verification as failed for that link

#### Scenario: Non-HTTP verification URL is blocked
- **WHEN** a verification request targets a link URL or backlink scan URL using a non-HTTP(S)
  scheme
- **THEN** the system rejects that fetch before connecting
- **AND** records the corresponding verification result as failed

#### Scenario: Verification redirects remain within the safety boundary
- **WHEN** a verification fetch receives a redirect
- **THEN** the system validates the redirect target before following it

#### Scenario: Verification response size is bounded
- **WHEN** a verification response exceeds the configured maximum response size
- **THEN** the system stops processing that response
- **AND** records the corresponding verification result as failed
