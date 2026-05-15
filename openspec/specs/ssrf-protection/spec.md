## ADDED Requirements

### Requirement: URL validation prevents DNS Rebinding attacks
The system SHALL resolve the URL hostname to IP addresses once, reject any private/reserved IPs, and then connect to the validated public IP directly while preserving the original Host header.

#### Scenario: DNS Rebinding attack is blocked
- **WHEN** a link detail request is made with a URL whose DNS resolves to a public IP during validation but would resolve to a private IP during connection
- **THEN** the system connects to the already-validated public IP and does not follow the rebinding

#### Scenario: Direct private IP access is blocked
- **WHEN** a link detail request is made with a URL pointing to `http://192.168.1.1/`
- **THEN** the system rejects the request with an error indicating private address access is not allowed

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
