## ADDED Requirements

### Requirement: RSS feed requests are protected from SSRF
The system SHALL apply URL validation to RSS feed discovery and RSS feed fetching before making any server-side network request.

#### Scenario: Private feed URL is blocked
- **WHEN** a feed discovery or feed fetch request targets a URL resolving to a private or reserved address
- **THEN** the system rejects the request before connecting

#### Scenario: Non-HTTP feed URL is blocked
- **WHEN** a feed discovery or feed fetch request targets a URL using a non-HTTP(S) scheme
- **THEN** the system rejects the request before connecting

### Requirement: RSS redirects remain within the safety boundary
The system SHALL validate every redirect target followed during RSS feed discovery and fetching.

#### Scenario: Redirect to private address is blocked
- **WHEN** a public feed URL redirects to a private or reserved address
- **THEN** the system rejects the redirect and records the feed fetch as failed

#### Scenario: Redirect limit is enforced
- **WHEN** a feed discovery or feed fetch request exceeds the configured redirect limit
- **THEN** the system stops following redirects and records the request as failed

### Requirement: RSS responses are bounded
The system SHALL enforce timeout and maximum response size limits when fetching RSS discovery pages and feed documents.

#### Scenario: Feed response exceeds maximum size
- **WHEN** a feed discovery or feed fetch response exceeds the configured maximum response size
- **THEN** the system stops processing the response and records the request as failed

#### Scenario: Feed request times out
- **WHEN** a feed discovery or feed fetch request exceeds the configured timeout
- **THEN** the system stops the request and records the request as failed
