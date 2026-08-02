## ADDED Requirements

### Requirement: Feed item hidden state
The system SHALL store a reversible site-level hidden state for each cached RSS/Atom feed item and
SHALL scope that state to the item's exact stable ID.

#### Scenario: New feed item defaults to visible
- **WHEN** the system caches a feed item that has not been seen before
- **THEN** the item is stored with hidden state set to false

#### Scenario: Existing hidden state survives refresh
- **WHEN** a subsequent refresh returns a feed item with the same stable ID as a hidden cached item
- **THEN** the system updates refreshed item content without changing the hidden state
- **AND** it preserves the existing read, favorite, and read-later states

#### Scenario: Unhide preserves saved and read states
- **WHEN** the Console changes a hidden feed item to visible
- **THEN** the system preserves that item's read, favorite, and read-later states

#### Scenario: Different stable identity is not implicitly hidden
- **WHEN** a source entry receives a different stable ID because its feed URL or source-local
  identity changes
- **THEN** the system treats it as a different visible cached item
- **AND** the system does not infer hidden state from URL, content hash, or another feed item

### Requirement: SQLite hidden-state startup compatibility
The system SHALL add hidden-state storage to new and existing SQLite feed databases idempotently
without changing the current SQLite schema version.

#### Scenario: New SQLite database includes hidden state
- **WHEN** the plugin initializes a new SQLite feed database
- **THEN** the feed item table includes a non-null hidden column whose default is false
- **AND** the database keeps the current schema version

#### Scenario: Existing database is opened without hidden state
- **WHEN** the plugin opens a valid existing feed database whose feed item table lacks the hidden
  column
- **THEN** schema initialization adds the hidden column before feed item operations become available
- **AND** every existing feed item defaults to visible
- **AND** the database keeps the current schema version

#### Scenario: Compatible database is opened again
- **WHEN** schema initialization finds that the hidden column already exists
- **THEN** it does not attempt to add the column again
- **AND** existing hidden values remain unchanged

#### Scenario: Older snapshot is restored
- **WHEN** startup restores a valid current-version snapshot whose feed item table lacks the hidden
  column
- **THEN** opening the restored active database adds the hidden column idempotently
- **AND** the restored feed items remain available and default to visible

#### Scenario: Hidden column initialization fails
- **WHEN** adding the hidden column or its supporting index fails
- **THEN** RSS storage remains unavailable for that plugin run
- **AND** the system does not isolate, replace, or clear the otherwise valid database as corrupt
- **AND** startup retries the compatibility step on the next plugin run

### Requirement: Hidden feed item Console queries
The system SHALL let authenticated Console clients list hidden or visible cached feed items with
cursor pagination and obtain an exact total hidden-item count.

#### Scenario: Hidden filter is omitted
- **WHEN** the Console lists feed items without a hidden query parameter
- **THEN** the system treats the requested hidden state as false
- **AND** the result contains no hidden items

#### Scenario: Hidden items are listed
- **WHEN** the Console lists feed items with hidden state set to true
- **THEN** the system returns only hidden items ordered and cursor-paginated using the normal feed
  item ordering

#### Scenario: Hidden filter combines with existing filters
- **WHEN** the Console lists feed items with hidden state and any supported link, group, read,
  favorite, or read-later filters
- **THEN** the system applies every filter before cursor pagination

#### Scenario: Hidden item count is requested
- **WHEN** the Console requests the hidden-item count
- **THEN** the system returns the exact number of cached items whose hidden state is true
- **AND** it does not require loading a feed item page

### Requirement: Feed item hidden-state command
The system SHALL expose one authenticated Console command that sets hidden state for an explicit
set of stable feed item IDs in one SQLite transaction.

#### Scenario: One item is hidden
- **WHEN** the Console submits one existing visible item ID with the desired hidden state true
- **THEN** the system hides that item
- **AND** the response reports one requested item and one updated item

#### Scenario: Selected items are hidden together
- **WHEN** the Console submits multiple distinct existing visible item IDs with the desired hidden
  state true
- **THEN** the system hides all selected items in one transaction
- **AND** the response reports the deduplicated requested count and actual updated count

#### Scenario: Selected items are unhidden together
- **WHEN** the Console submits existing hidden item IDs with the desired hidden state false
- **THEN** the system makes those items visible in one transaction
- **AND** their existing read, favorite, and read-later states remain unchanged

#### Scenario: Duplicate IDs are submitted
- **WHEN** the request contains the same non-blank item ID more than once
- **THEN** the system applies the requested state once for that ID
- **AND** the requested count includes that ID once

#### Scenario: Empty ID set is submitted
- **WHEN** the request contains no item IDs
- **THEN** the system rejects the request as invalid without changing feed items

#### Scenario: Requested item is already in the desired state
- **WHEN** an existing item already has the requested hidden state
- **THEN** the operation succeeds without changing that row
- **AND** the item is not included in the updated count

#### Scenario: Requested item no longer exists
- **WHEN** the request includes an unknown item ID alongside zero or more existing IDs
- **THEN** the system ignores the unknown ID
- **AND** it still applies the requested state to existing IDs
- **AND** the response requested count still includes the deduplicated unknown ID

#### Scenario: Batch update fails in storage
- **WHEN** a storage error occurs while changing one or more requested items
- **THEN** the system rolls back every hidden-state change from that request
- **AND** it returns the existing RSS storage-unavailable failure contract

### Requirement: Hidden feed item visibility and accounting
The system SHALL exclude hidden feed items from normal reading and public visibility while retaining
them as physical cached items.

#### Scenario: Normal Console list excludes hidden items
- **WHEN** the Console loads the primary feed list, favorite list, or read-later list
- **THEN** the result excludes hidden items even when they match the other requested states

#### Scenario: Public REST query excludes hidden items
- **WHEN** an anonymous client queries public RSS/Atom feed items
- **THEN** the response excludes hidden items
- **AND** the public item model does not expose hidden state

#### Scenario: Theme Finder excludes hidden items
- **WHEN** a theme obtains RSS/Atom feed items through the Finder
- **THEN** Finder results exclude hidden items
- **AND** the theme cannot request hidden items

#### Scenario: Cache counts include hidden items
- **WHEN** the system calculates physical per-feed, per-link, aggregate, or cleanup-result cache
  counts
- **THEN** those counts include hidden items

#### Scenario: Hidden items keep a feed cache non-empty
- **WHEN** a feed URL has hidden cached items and usable fresh conditional validators
- **THEN** refresh treats the local cache as non-empty when deciding whether to send conditional
  request headers

### Requirement: Hidden feed item retention protection
The system SHALL protect hidden feed items from normal RSS/Atom cache retention while preserving
Link deletion cleanup.

#### Scenario: Age limit excludes hidden items
- **WHEN** retention cleanup deletes cached items older than the configured age limit
- **THEN** it does not delete hidden items

#### Scenario: Per-link limit excludes hidden items
- **WHEN** retention cleanup deletes excess cached items for a link
- **THEN** it deletes only items that are not hidden, favorite, or read-later

#### Scenario: Global limit excludes hidden items
- **WHEN** retention cleanup deletes excess cached items globally
- **THEN** it deletes only items that are not hidden, favorite, or read-later

#### Scenario: Link deletion removes hidden items
- **WHEN** a `Link` enters deletion with hidden cached feed items
- **THEN** the system removes those hidden items with the rest of that Link's feed cache before
  deletion is finalized

### Requirement: Console hidden feed item workflow
The Console RSS updates view SHALL provide focused single-item and explicit-selection workflows for
hiding and restoring cached feed items.

#### Scenario: User hides one item
- **WHEN** the user activates hide on one primary-list item and confirms the action
- **THEN** the Console hides the item and removes it from the normal list

#### Scenario: User enters batch hide mode
- **WHEN** the user activates batch hide mode on the primary feed list
- **THEN** the Console shows selection controls for currently loaded items
- **AND** it offers select-all-loaded, cancel, and hide-selected actions

#### Scenario: User selects all loaded items
- **WHEN** the user activates select-all-loaded in batch hide mode
- **THEN** the Console selects only feed items currently loaded in the client
- **AND** it does not select unloaded items that match the current filters

#### Scenario: User confirms batch hide
- **WHEN** the user confirms hiding selected loaded items
- **THEN** the Console submits their explicit stable IDs in one batch command
- **AND** it exits selection mode after the operation succeeds

#### Scenario: Selection context changes
- **WHEN** the user changes subscription or read filters, reloads data, or completes a successful
  hide or unhide selection operation
- **THEN** the Console clears the applicable current selection

#### Scenario: User opens hidden items
- **WHEN** the user activates the "已隐藏" entry in the RSS updates page header
- **THEN** the Console opens a separate cursor-loaded list of hidden items
- **AND** the entry displays the exact hidden-item count

#### Scenario: Hidden list keeps focused actions
- **WHEN** the Console displays a hidden feed item
- **THEN** it displays existing read, favorite, and read-later states without offering state edits
- **AND** it offers external open and single-item unhide actions

#### Scenario: User opens a hidden item article
- **WHEN** the user opens the external article URL from the hidden-item list
- **THEN** the Console keeps the item hidden, marks it read, removes it from read-later, and preserves
  favorite state

#### Scenario: User unhides one item
- **WHEN** the user activates single-item unhide
- **THEN** the Console performs the operation without an additional confirmation
- **AND** the item immediately becomes eligible for normal Console, public REST, and Finder results

#### Scenario: User confirms batch unhide
- **WHEN** the user selects hidden items and confirms batch unhide
- **THEN** the Console submits their explicit stable IDs in one batch command
- **AND** the restored items immediately become eligible for normal Console, public REST, and Finder
  results

#### Scenario: Destructive-looking actions require confirmation
- **WHEN** the user requests single hide, batch hide, or batch unhide
- **THEN** the Console presents a confirmation describing the selected scope before changing state

#### Scenario: Successful hidden-state mutation refreshes dependent data
- **WHEN** a hide or unhide command succeeds
- **THEN** the Console refetches normal feed queries, hidden feed queries, unread summaries, and the
  hidden-item count
- **AND** it reports success without duplicating Halo's global HTTP failure feedback

## MODIFIED Requirements

### Requirement: Bulk feed item mark-read API
The system SHALL expose a Console API operation that marks visible cached unread RSS/Atom feed items
as read without requiring the Console to provide individual item IDs.

#### Scenario: All unread items are marked read
- **WHEN** the Console submits the bulk mark-read operation without a link scope
- **THEN** the system marks every visible cached feed item whose read state is false as read
- **AND** hidden items keep their existing read state
- **AND** the system returns the number of visible items updated

#### Scenario: Selected link unread items are marked read
- **WHEN** the Console submits the bulk mark-read operation with a link metadata name
- **THEN** the system marks every visible cached feed item for that link whose read state is false as
  read
- **AND** hidden items and cached feed items for other links keep their existing read state
- **AND** the system returns the number of visible items updated

#### Scenario: Already read items are not counted
- **WHEN** the bulk mark-read operation matches visible cached feed items that are already read
- **THEN** those items remain read
- **AND** they are not included in the returned updated count

#### Scenario: Empty bulk mark-read scope is a no-op
- **WHEN** the bulk mark-read operation matches no visible unread cached feed items
- **THEN** the system does not create feed item records
- **AND** the system returns an updated count of 0

### Requirement: Feed unread summary API
The system SHALL expose a lightweight Console API operation that reports visible unread RSS/Atom
feed item counts without requiring the Console to load feed item pages.

#### Scenario: Aggregate unread count is returned
- **WHEN** the Console requests the RSS unread summary
- **THEN** the system returns the number of non-hidden cached feed items whose read state is false
  across all RSS subscriptions

#### Scenario: Per-link unread counts are returned
- **WHEN** the Console requests the RSS unread summary
- **THEN** the system returns non-hidden unread cached feed item counts keyed by link metadata name
- **AND** each per-link count includes only non-hidden cached feed items associated with that link

#### Scenario: Read items are excluded
- **WHEN** cached feed items for a link include read, unread, visible, and hidden states
- **THEN** the unread summary counts only items whose read state and hidden state are both false

#### Scenario: Missing unread items produce zero counts
- **WHEN** the unread summary does not include a per-link count for a subscribed link
- **THEN** the Console treats that link's visible unread count as zero
