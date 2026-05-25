## ADDED Requirements

### Requirement: Link RSS configuration
The system SHALL allow each `Link` to optionally configure RSS/Atom tracking through `spec.rss`.

#### Scenario: RSS is disabled by default
- **WHEN** an existing link has no `spec.rss` value
- **THEN** the system treats RSS tracking as disabled for that link

#### Scenario: User enables RSS tracking with a feed URL
- **WHEN** a user saves a link with `spec.rss.enabled` set to `true` and `spec.rss.feedUrl` set to an absolute HTTP or HTTPS URL
- **THEN** the system persists the RSS configuration on the `Link` resource

#### Scenario: User enables RSS tracking for the first time
- **WHEN** a user creates a link with RSS tracking enabled or enables RSS tracking on an existing link
- **THEN** the Console starts an initial RSS refresh after saving the link

#### Scenario: User disables RSS tracking
- **WHEN** a user sets `spec.rss.enabled` to `false`
- **THEN** the system excludes that link from scheduled RSS refreshes

### Requirement: Link RSS status
The system SHALL store lightweight RSS runtime state on `Link.status.rss`.

#### Scenario: Feed fetch succeeds
- **WHEN** the system successfully refreshes a link feed
- **THEN** it updates `status.rss` with the effective feed URL, latest success time, conditional request metadata, latest published item time, and item count

#### Scenario: Feed fetch fails
- **WHEN** the system fails to refresh a link feed
- **THEN** it records the failure message and failure count in `status.rss`

#### Scenario: Feed item content is not stored in status
- **WHEN** the system updates `status.rss`
- **THEN** it does not store feed item arrays, raw feed XML, article HTML, or article summaries in the `Link` resource

### Requirement: Embedded feed item store
The system SHALL store RSS/Atom feed items in a plugin-local embedded database instead of Halo Extension resources.

#### Scenario: Feed item is cached
- **WHEN** the system parses a feed item from an enabled link
- **THEN** it stores the item in the embedded feed item store with link name, feed URL, stable item ID, item URL, title, summary, author, published time, updated time, fetched time, and content hash

#### Scenario: Same item is refreshed again
- **WHEN** a subsequent refresh returns a feed item with the same stable identity
- **THEN** the system updates the existing cached item instead of inserting a duplicate

#### Scenario: Large feed cache does not create Extension items
- **WHEN** the cached feed item count reaches 100000 records
- **THEN** the system still stores those records outside Halo Extension storage

### Requirement: Feed discovery
The system SHALL support discovering RSS/Atom feed URLs for a link website URL.

#### Scenario: Feed link is discovered from HTML
- **WHEN** the user requests feed discovery for a website that exposes an RSS or Atom `<link>` element
- **THEN** the system returns the discovered feed URL without enabling RSS automatically

#### Scenario: No feed is discovered
- **WHEN** the user requests feed discovery for a website without a discoverable feed
- **THEN** the system returns an empty result and leaves the link RSS configuration unchanged

### Requirement: Manual feed refresh
The system SHALL allow users to manually refresh RSS for an enabled link from the Console.

#### Scenario: Manual refresh inserts new items
- **WHEN** a user manually refreshes a link with RSS enabled
- **THEN** the system fetches the effective feed URL, caches new feed items, and updates `status.rss`

#### Scenario: Manual refresh rejects disabled link
- **WHEN** a user manually refreshes a link with RSS disabled
- **THEN** the system rejects the refresh request without fetching the feed URL

### Requirement: Scheduled feed refresh
The system SHALL refresh enabled link feeds on a background schedule.

#### Scenario: Scheduled refresh processes enabled links
- **WHEN** the scheduled RSS refresh runs
- **THEN** the system refreshes links whose `spec.rss.enabled` is `true`

#### Scenario: Scheduled refresh skips disabled links
- **WHEN** the scheduled RSS refresh runs
- **THEN** the system does not fetch feeds for links whose `spec.rss.enabled` is absent or `false`

### Requirement: Cursor-based feed item listing
The system SHALL expose a Console API for listing cached feed items with cursor pagination.

#### Scenario: First page is listed
- **WHEN** the Console requests recent feed items with a limit
- **THEN** the system returns at most that many items ordered by published time descending and stable ID descending

#### Scenario: Next page is listed
- **WHEN** the Console requests feed items with a cursor containing the previous page boundary
- **THEN** the system returns items older than that cursor without repeating items from the previous page

#### Scenario: Items are filtered by link
- **WHEN** the Console requests feed items for a specific link
- **THEN** the system returns only cached items associated with that link

#### Scenario: Items are filtered by group
- **WHEN** the Console requests feed items for a specific link group
- **THEN** the system returns only cached items for links currently assigned to that group

#### Scenario: Items are filtered by read state
- **WHEN** the Console requests feed items by read or unread state
- **THEN** the system returns only cached items matching that read state

#### Scenario: Item read state is updated
- **WHEN** the Console marks a cached feed item as read or unread
- **THEN** the system persists that read state in the embedded feed item store

### Requirement: Feed item retention
The system SHALL enforce retention limits for cached RSS/Atom feed items.

#### Scenario: Per-link item limit is exceeded
- **WHEN** a link has more cached feed items than the configured per-link limit
- **THEN** the system deletes the oldest cached items for that link until the limit is satisfied

#### Scenario: Global item limit is exceeded
- **WHEN** the embedded feed item store contains more records than the configured global limit
- **THEN** the system deletes the oldest cached feed items until the limit is satisfied

#### Scenario: Age limit is exceeded
- **WHEN** a cached feed item is older than the configured retention age
- **THEN** the system deletes that cached feed item during retention cleanup

### Requirement: Console RSS updates view
The system SHALL provide a Console view for reading recent RSS/Atom updates from friend links.

#### Scenario: Recent updates are displayed
- **WHEN** the user opens the RSS updates view
- **THEN** the Console displays recent cached feed items with title, source link, publication time, summary, and external article URL

#### Scenario: Recent updates are filtered by read state
- **WHEN** the user selects all, unread, or read items
- **THEN** the Console reloads the recent updates list with that read-state filter

#### Scenario: Link filter choices are limited to subscribed links
- **WHEN** the user opens the RSS updates view link filter
- **THEN** the Console lists only links with RSS tracking enabled and a feed URL configured

#### Scenario: Feed status is visible on a link
- **WHEN** a link has RSS tracking configured
- **THEN** the Console displays whether the latest refresh succeeded or failed

#### Scenario: Item text is rendered safely
- **WHEN** a feed item title or summary contains HTML markup
- **THEN** the Console renders it as sanitized or plain text content
