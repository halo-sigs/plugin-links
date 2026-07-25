## MODIFIED Requirements

### Requirement: Link RSS configuration
The system SHALL allow each `Link` to optionally configure RSS/Atom tracking through `spec.rss` with
a Link-level enable switch and a list of feed URLs, and SHALL start initial refresh from the
backend when an approved LinkApplication creates an enabled subscription.

#### Scenario: RSS is disabled by default
- **WHEN** an existing link has no `spec.rss` value
- **THEN** the system treats RSS tracking as disabled for that link

#### Scenario: User enables RSS tracking with feed URLs
- **WHEN** a user saves a link with `spec.rss.enabled` set to `true` and
  `spec.rss.feedUrls` containing one or more absolute HTTP or HTTPS URLs
- **THEN** the system persists the RSS configuration on the `Link` resource
- **AND** the system does not persist `spec.rss.feedUrl`

#### Scenario: Enabled RSS requires at least one feed URL
- **WHEN** a user saves a link with `spec.rss.enabled` set to `true` and
  `spec.rss.feedUrls` absent, empty, or containing only blank values
- **THEN** the system rejects the RSS configuration without enabling RSS tracking

#### Scenario: User enables RSS tracking for the first time
- **WHEN** a user creates a link with RSS tracking enabled or enables RSS tracking on an existing
  link
- **THEN** the Console starts an initial RSS refresh after saving the link
- **AND** the Console does not wait for the initial RSS refresh to complete before completing the
  save modal flow

#### Scenario: Application approval creates an enabled subscription
- **WHEN** backend LinkApplication approval creates a Link with RSS tracking enabled
- **THEN** the backend starts an initial RSS refresh for that Link
- **AND** Console does not need to trigger refresh from its approval-success callback

#### Scenario: Initial RSS refresh failure does not block saved link
- **WHEN** the initial RSS refresh started after a successful Link save or application approval fails
- **THEN** the saved `Link` remains persisted with its RSS configuration
- **AND** the LinkApplication remains `APPROVED` when the Link came from approval
- **AND** the Console does not reopen or keep open the save modal because of the initial RSS refresh
  failure

#### Scenario: User disables RSS tracking
- **WHEN** a user sets `spec.rss.enabled` to `false`
- **THEN** the system excludes that link from scheduled RSS refreshes

#### Scenario: Feed URLs do not create feed-level subscriptions
- **WHEN** a link has multiple values in `spec.rss.feedUrls`
- **THEN** the system treats the link as one RSS subscription
- **AND** the system does not require per-feed enablement, names, groups, or sidebar identities
