## ADDED Requirements

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

## MODIFIED Requirements

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
