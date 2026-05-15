## ADDED Requirements

### Requirement: Block private and reserved IP addresses
The system SHALL reject any URL whose resolved host address falls within a private, loopback, or link-local range before opening an HTTP connection.

#### Scenario: IPv4 loopback is blocked
- **WHEN** the admin provides `http://127.0.0.1/actuator`
- **THEN** the system raises a validation error and does not connect

#### Scenario: IPv4 private ranges are blocked
- **WHEN** the admin provides `http://10.0.0.1/`, `http://172.16.0.1/`, or `http://192.168.1.1/`
- **THEN** the system raises a validation error and does not connect

#### Scenario: IPv6 loopback is blocked
- **WHEN** the admin provides `http://[::1]/`
- **THEN** the system raises a validation error and does not connect

#### Scenario: IPv6 link-local is blocked
- **WHEN** the admin provides `http://[fe80::1]/`
- **THEN** the system raises a validation error and does not connect

#### Scenario: Public URLs are allowed
- **WHEN** the admin provides `https://example.com`
- **THEN** the system proceeds to fetch the page

### Requirement: Restrict allowed URL schemes
The system SHALL only permit URLs using the `http` or `https` scheme.

#### Scenario: File scheme is blocked
- **WHEN** the admin provides `file:///etc/passwd`
- **THEN** the system raises a validation error and does not connect

#### Scenario: FTP scheme is blocked
- **WHEN** the admin provides `ftp://internal.ftp.server/resource`
- **THEN** the system raises a validation error and does not connect

### Requirement: Validate redirect targets
The system SHALL validate every redirect target against the same IP and scheme filters used for the initial URL. The system SHALL follow no more than 3 redirects.

#### Scenario: Redirect to private IP is blocked
- **WHEN** a public URL responds with a `Location: http://192.168.1.1/` header
- **THEN** the system raises a validation error and does not connect to the private address

#### Scenario: Excessive redirects are blocked
- **WHEN** a URL chains more than 3 redirects
- **THEN** the system stops following and raises a validation error

#### Scenario: Scheme downgrade in redirect is blocked
- **WHEN** an `https` URL redirects to `ftp://...`
- **THEN** the system raises a validation error and does not connect
