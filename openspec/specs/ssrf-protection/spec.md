# ssrf-protection Specification

## Purpose
Define URL-fetch safety requirements that prevent server-side requests from reaching private, reserved, or otherwise unsafe network targets.

## Requirements

### Requirement: URL validation prevents DNS Rebinding attacks
The system SHALL use Halo's SSRF-safe HTTP client for outbound external HTTP(S)
connections so private, local, and special-use resolved addresses are rejected
before a connection is opened.

#### Scenario: DNS Rebinding attack is blocked
- **WHEN** a link detail request is made with a URL whose hostname resolves to a
  private, local, or special-use address at connection time
- **THEN** the system rejects the request before connecting

#### Scenario: Direct private IP access is blocked
- **WHEN** a link detail request is made with a URL pointing to `http://192.168.1.1/`
- **THEN** the system rejects the request with an error indicating private address access is not allowed

### Requirement: External fetches use Halo HTTP security utilities
The system SHALL execute plugin-originated external HTTP(S) requests through
Halo API-provided HTTP security utilities.

#### Scenario: Link detail fetch uses the secure client
- **WHEN** the system fetches an external URL to extract link detail metadata
- **THEN** it uses an HTTP client based on `HttpSecurityUtils.secureHttpClient()`

#### Scenario: RSS feed fetch uses the secure client
- **WHEN** the system fetches an external URL for RSS/Atom discovery or feed refresh
- **THEN** it uses an HTTP client based on `HttpSecurityUtils.secureHttpClient()`

#### Scenario: Link verification fetch uses the secure client
- **WHEN** the system fetches an external URL for reachability or backlink verification
- **THEN** it uses an HTTP client based on `HttpSecurityUtils.secureHttpClient()`

#### Scenario: Response size filter is applied
- **WHEN** the system consumes an external HTTP response body
- **THEN** it applies `HttpSecurityUtils.maxResponseSizeFilter(long)` using the
  fetch operation's configured maximum response size

#### Scenario: Plugin runtime requirement matches the security utility dependency
- **WHEN** the plugin is packaged
- **THEN** the plugin manifest declares Halo `>=2.25.0` as the minimum runtime
  version

### Requirement: IPv6 ULA addresses are blocked
The system SHALL treat IPv6 Unique Local Addresses (`fc00::/7`) as private and block them.

#### Scenario: IPv6 ULA access is blocked
- **WHEN** a link detail request is made with a URL pointing to `http://[fc00::1]/`
- **THEN** the system rejects the request with an error indicating private address access is not allowed

#### Scenario: IPv6 public address is allowed
- **WHEN** a link detail request is made with a URL pointing to `http://[2001:db8::1]/`
- **THEN** the system proceeds with fetching the link detail

### Requirement: Invalid URLs return HTTP 400
The system SHALL return HTTP 400 (Bad Request) for link detail requests with malformed URLs, blocked URLs, or URLs using non-HTTP(S) schemes.

#### Scenario: Invalid URL returns 400
- **WHEN** a link detail request is made with an invalid URL such as `not-a-url`
- **THEN** the system returns HTTP 400 with an error message indicating the URL is invalid

#### Scenario: Blocked URL returns 400
- **WHEN** a link detail request is made with a URL resolving to a private IP
- **THEN** the system returns HTTP 400 with an error message indicating the URL is blocked for security reasons

#### Scenario: Non-HTTP scheme returns 400
- **WHEN** a link detail request is made with a URL using the `ftp://` scheme
- **THEN** the system returns HTTP 400 with an error message indicating only HTTP and HTTPS are allowed

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
