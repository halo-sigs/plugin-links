## MODIFIED Requirements

### Requirement: Embedded feed item store
The system SHALL store RSS/Atom feed items in a plugin-local SQLite database instead of Halo
Extension resources.

#### Scenario: Feed item is cached
- **WHEN** the system parses a feed item from an enabled link feed URL
- **THEN** it stores the item in SQLite with link name, feed URL, stable item ID, item URL, title,
  summary, author, published time, updated time, first-seen time, fetched time, and content hash

#### Scenario: Same item is refreshed again from the same feed URL
- **WHEN** a subsequent refresh returns a feed item with the same stable identity from the same
  link name and feed URL
- **THEN** the system updates the existing cached item instead of inserting a duplicate
- **AND** the system preserves the item's first-seen time while updating refreshed item data

#### Scenario: Same source-local identity appears in another feed URL
- **WHEN** two configured feed URLs under the same link return feed items with the same
  source-local stable identity
- **THEN** the system does not let one feed URL overwrite the other feed URL's cached item

#### Scenario: Large feed cache does not create Extension items
- **WHEN** the cached feed item count reaches 100000 records
- **THEN** the system still stores those records outside Halo Extension storage

## ADDED Requirements

### Requirement: Legacy Nitrite feed item migration
The system SHALL perform a one-time migration from a legacy Nitrite feed item database before
using SQLite as the active feed item store.

#### Scenario: Readable legacy database is migrated
- **WHEN** the plugin starts with a legacy Nitrite database and without a completed migration
  marker
- **THEN** the system copies every readable feed item into a temporary SQLite database
- **AND** it preserves the item identity, link name, feed URL, URL, title, summary, author,
  published time, updated time, first-seen time, fetched time, content hash, read state, favorite
  state, and read-later state

#### Scenario: Migration is validated before cutover
- **WHEN** all readable legacy records have been processed
- **THEN** the system validates the temporary SQLite schema version, migrated record counts, and
  SQLite consistency
- **AND** it atomically promotes the temporary database only after validation succeeds

#### Scenario: Individual legacy record is unreadable
- **WHEN** the migration cannot decode one legacy feed item record
- **THEN** the system records a structured error for that record when identifying information is
  available
- **AND** it continues migrating the remaining readable records
- **AND** the skipped record does not cause a persistent Console warning

#### Scenario: Legacy database is entirely unreadable
- **WHEN** the migration cannot open or enumerate the legacy Nitrite database
- **THEN** the system attempts recognized Nitrite JSON backups from newest to oldest
- **AND** it migrates the first backup that can be imported and read
- **AND** it isolates the unreadable live legacy database when a backup succeeds

#### Scenario: No legacy source is readable
- **WHEN** neither the live legacy Nitrite database nor any recognized Nitrite JSON backup can be
  imported and read
- **THEN** the system isolates the unreadable live legacy database when it exists
- **AND** it initializes an empty SQLite feed item database
- **AND** it completes the storage cutover without blocking link management

#### Scenario: Migration fails before cutover
- **WHEN** a temporary SQLite write, validation, or promotion fails before migration is marked
  complete
- **THEN** the system leaves the legacy Nitrite database unchanged
- **AND** it does not expose the temporary database as the active store
- **AND** it retries a full migration on the next plugin start

### Requirement: Legacy migration completion boundary
The system SHALL persist a migration completion marker independently of the active SQLite file so
that legacy data is never imported again after a completed cutover.

#### Scenario: Completed migration is not repeated
- **WHEN** the plugin starts after migration has been marked complete
- **THEN** the system does not open or import the legacy Nitrite database
- **AND** the result is unchanged when the active SQLite file is absent, isolated, or rebuilt

#### Scenario: Cutover is interrupted before the external marker is written
- **WHEN** an atomically promoted SQLite database records internally that migration completed but
  the external completion marker is absent
- **THEN** the system recreates the external marker
- **AND** it does not repeat the migration

#### Scenario: Legacy source is retained after migration
- **WHEN** migration completes successfully
- **THEN** the system keeps the original legacy Nitrite database unchanged as a read-only
  migration-time snapshot
- **AND** normal RSS operations never write to that legacy database

### Requirement: SQLite feed item durability mode
The system SHALL configure the active SQLite feed item database for transactional, serialized
access with write-ahead logging and full synchronous durability.

#### Scenario: Feed item write succeeds
- **WHEN** an RSS operation performs one or more related feed item writes
- **THEN** the system commits those writes in an explicit SQLite transaction
- **AND** concurrent feed item operations do not use the shared connection simultaneously

#### Scenario: Feed item write fails
- **WHEN** a feed item transaction fails before commit
- **THEN** the system rolls back the transaction without exposing a partial update

#### Scenario: Plugin storage lifecycle ends
- **WHEN** the last SQLite database component owned by the plugin is destroyed
- **THEN** the system closes its connection
- **AND** it deregisters SQLite JDBC drivers loaded by the retiring plugin classloader
- **AND** a later plugin database lifecycle can register the driver and open SQLite again

### Requirement: SQLite feed item snapshots
The system SHALL maintain validated local snapshots of the active SQLite feed item database.

#### Scenario: Initial post-migration snapshot is created
- **WHEN** a Nitrite migration and SQLite cutover complete successfully
- **THEN** the system creates and validates a consistent SQLite snapshot immediately

#### Scenario: Daily snapshot is created
- **WHEN** no validated snapshot has been created within the configured daily interval
- **THEN** the system creates a new snapshot using a SQLite-consistent backup operation
- **AND** it validates the new snapshot before making it eligible for recovery

#### Scenario: Snapshot retention is enforced
- **WHEN** a third validated snapshot is created
- **THEN** the system retains the two newest validated snapshots
- **AND** it removes the older snapshot only after the new snapshot passes validation

#### Scenario: Snapshot creation fails
- **WHEN** creation or validation of a new snapshot fails
- **THEN** the active SQLite database and existing valid snapshots remain unchanged
- **AND** RSS operations continue
- **AND** the system records the failure without creating a persistent Console warning

### Requirement: SQLite startup consistency recovery
The system SHALL verify the active feed item database at plugin startup and automatically recover
or rebuild the cache when consistency cannot be established.

#### Scenario: Active database passes startup validation
- **WHEN** the active SQLite database passes `quick_check`
- **THEN** the system opens it as the active feed item store without restoring a snapshot

#### Scenario: Active database fails startup validation
- **WHEN** the active SQLite database cannot be opened or fails `quick_check`
- **THEN** the system validates retained snapshots from newest to oldest
- **AND** it restores the first snapshot that passes validation
- **AND** it isolates the failed active database and its WAL and shared-memory sidecar files

#### Scenario: No retained snapshot is valid
- **WHEN** the active database and every retained snapshot fail validation
- **THEN** the system isolates the failed active database and its WAL and shared-memory sidecar
  files
- **AND** it creates a new empty SQLite feed item database
- **AND** it does not repeat the legacy Nitrite migration

#### Scenario: Quarantined database retention is enforced
- **WHEN** a newly failed database file group is isolated
- **THEN** the system retains only the most recent isolated main database, WAL, and shared-memory
  file group
- **AND** validated daily snapshots remain subject to their separate two-snapshot retention rule

### Requirement: Runtime RSS storage failure isolation
The system SHALL isolate persistent SQLite runtime failures to RSS functionality and defer file
replacement or snapshot recovery until the next plugin startup.

#### Scenario: Persistent runtime storage failure occurs
- **WHEN** bounded handling of a transient SQLite error does not restore normal database access
- **THEN** the system stops scheduled RSS refresh and feed item reads and writes for the current
  plugin run
- **AND** it does not replace the open database files while the plugin is running

#### Scenario: Console accesses unavailable RSS storage
- **WHEN** RSS storage is unavailable for the current plugin run
- **THEN** Console RSS feed item operations return an explicit service-unavailable error
- **AND** link and link-group management operations remain available

#### Scenario: Theme accesses unavailable RSS storage
- **WHEN** a theme-facing RSS lookup occurs while RSS storage is unavailable
- **THEN** the lookup returns no cached feed items without failing theme rendering

#### Scenario: Link deletion needs unavailable RSS storage
- **WHEN** a Link enters deletion while RSS storage is unavailable
- **THEN** the system does not finalize deletion until its cached feed items can be removed
