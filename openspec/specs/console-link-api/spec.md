## ADDED Requirements

### Requirement: Console link list endpoint
The system SHALL expose a `GET` endpoint at `/apis/console.api.link.halo.run/v1alpha1/links` that returns a paginated list of links, supporting keyword search and group name filtering.

#### Scenario: List all links
- **WHEN** a console user sends `GET /apis/console.api.link.halo.run/v1alpha1/links`
- **THEN** the system returns a paginated list of all links sorted by priority and creation timestamp

#### Scenario: Filter links by keyword
- **WHEN** a console user sends `GET /apis/console.api.link.halo.run/v1alpha1/links?keyword=foo`
- **THEN** the system returns links whose display name, description, or URL contains "foo"

#### Scenario: Filter links by group
- **WHEN** a console user sends `GET /apis/console.api.link.halo.run/v1alpha1/links?groupName=default`
- **THEN** the system returns only links belonging to the group named "default"

### Requirement: Console link detail endpoint
The system SHALL expose a `GET` endpoint at `/apis/console.api.link.halo.run/v1alpha1/link-detail` that fetches metadata for an external URL.

#### Scenario: Fetch link detail
- **WHEN** a console user sends `GET /apis/console.api.link.halo.run/v1alpha1/link-detail?url=https://example.com`
- **THEN** the system returns the scraped title, description, and logo for that URL

#### Scenario: Reject invalid URL
- **WHEN** a console user sends `GET /apis/console.api.link.halo.run/v1alpha1/link-detail?url=not-a-url`
- **THEN** the system returns a 400 Bad Request error

### Requirement: Console link sort endpoint
The system SHALL expose a `POST` endpoint at `/apis/console.api.link.halo.run/v1alpha1/links/-/sort` that reorders links by priority.

#### Scenario: Sort links
- **WHEN** a console user sends `POST /apis/console.api.link.halo.run/v1alpha1/links/-/sort` with an ordered list of link names
- **THEN** the system updates each link's `spec.priority` to match the provided order

### Requirement: Console link group sort endpoint
The system SHALL expose a `POST` endpoint at `/apis/console.api.link.halo.run/v1alpha1/link-groups/-/sort` that reorders link groups by priority.

#### Scenario: Sort link groups
- **WHEN** a console user sends `POST /apis/console.api.link.halo.run/v1alpha1/link-groups/-/sort` with an ordered list of group names
- **THEN** the system updates each group's `spec.priority` to match the provided order
