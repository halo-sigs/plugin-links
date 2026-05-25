## MODIFIED Requirements

### Requirement: Embedded feed item store
The system SHALL store RSS/Atom feed items in a plugin-local embedded database instead of Halo Extension resources.

#### Scenario: Feed item is cached
- **WHEN** the system parses a feed item from an enabled link feed URL
- **THEN** it stores the item in the embedded feed item store with link name, feed URL, stable item ID, item URL, title, summary, author, published time, updated time, first-seen time, fetched time, and content hash

#### Scenario: Same item is refreshed again from the same feed URL
- **WHEN** a subsequent refresh returns a feed item with the same stable identity
  from the same link name and feed URL
- **THEN** the system updates the existing cached item instead of inserting a duplicate
- **AND** the system preserves the item's first-seen time while updating refreshed item data

#### Scenario: Same source-local identity appears in another feed URL
- **WHEN** two configured feed URLs under the same link return feed items with
  the same source-local stable identity
- **THEN** the system does not let one feed URL overwrite the other feed URL's
  cached item

#### Scenario: Large feed cache does not create Extension items
- **WHEN** the cached feed item count reaches 100000 records
- **THEN** the system still stores those records outside Halo Extension storage

### Requirement: Feed item retention
The system SHALL enforce retention limits for cached RSS/Atom feed items using local cache lifecycle age for age-based cleanup.

#### Scenario: Per-link item limit is exceeded
- **WHEN** a link has more cached feed items than the configured per-link limit
- **THEN** the system deletes the oldest cached items for that link until the limit is satisfied

#### Scenario: Global item limit is exceeded
- **WHEN** the embedded feed item store contains more records than the configured global limit
- **THEN** the system deletes the oldest cached feed items until the limit is satisfied

#### Scenario: Age limit is exceeded
- **WHEN** an unsaved cached feed item's first-seen time is older than the configured retention age
- **THEN** the system deletes that cached feed item during retention cleanup

#### Scenario: Remote publication time is older than the age limit
- **WHEN** an unsaved cached feed item's remote published time is older than the configured retention age
- **AND** the item's first-seen time is within the configured retention age
- **THEN** age-based retention does not delete that cached feed item
