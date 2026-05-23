## ADDED Requirements

### Requirement: Feed item saved states
The system SHALL store site-level favorite and read-later states for cached RSS/Atom feed
items in the embedded feed item store.

#### Scenario: New item defaults to unsaved states
- **WHEN** the system caches a feed item that has not been seen before
- **THEN** the item is stored with favorite and read-later states set to false

#### Scenario: Existing saved states survive refresh
- **WHEN** a subsequent refresh returns a feed item with the same stable identity
- **THEN** the system preserves the existing read, favorite, and read-later states while
  updating refreshed item content

#### Scenario: Favorite and read-later can coexist
- **WHEN** the Console marks the same cached feed item as favorite and read-later
- **THEN** the system persists both states as true for that item

### Requirement: Saved feed item listing filters
The system SHALL allow the Console to filter cached RSS/Atom feed items by favorite and
read-later state.

#### Scenario: Items are filtered by favorite state
- **WHEN** the Console requests feed items with a favorite-state filter
- **THEN** the system returns only cached items matching that favorite state

#### Scenario: Items are filtered by read-later state
- **WHEN** the Console requests feed items with a read-later-state filter
- **THEN** the system returns only cached items matching that read-later state

#### Scenario: Saved-state filters combine with existing filters
- **WHEN** the Console requests feed items with link, group, read, favorite, or read-later
  filters
- **THEN** the system applies all provided filters before returning the cursor-paginated page

### Requirement: Saved feed item state updates
The system SHALL allow the Console to update favorite and read-later state for cached
RSS/Atom feed items.

#### Scenario: Item favorite state is updated
- **WHEN** the Console marks a cached feed item as favorite or not favorite
- **THEN** the system persists that favorite state in the embedded feed item store

#### Scenario: Item read-later state is updated
- **WHEN** the Console marks a cached feed item as read-later or not read-later
- **THEN** the system persists that read-later state in the embedded feed item store

#### Scenario: Missing item state update is rejected
- **WHEN** the Console updates favorite or read-later state for an unknown cached feed item ID
- **THEN** the system returns a not-found response without creating a new item

### Requirement: Saved feed item retention protection
The system SHALL protect favorite and read-later feed items from normal RSS/Atom cache
retention cleanup.

#### Scenario: Age limit excludes saved items
- **WHEN** retention cleanup deletes cached feed items older than the configured age limit
- **THEN** it does not delete items whose favorite or read-later state is true

#### Scenario: Per-link limit excludes saved items
- **WHEN** retention cleanup deletes excess cached feed items for a link
- **THEN** it deletes only unsaved items for that link

#### Scenario: Global limit excludes saved items
- **WHEN** retention cleanup deletes excess cached feed items globally
- **THEN** it deletes only unsaved items

### Requirement: Console saved item workflows
The system SHALL expose favorite and read-later controls in the Console RSS updates view.

#### Scenario: User toggles favorite from the updates list
- **WHEN** the user toggles favorite on a feed item in the RSS updates view
- **THEN** the Console updates the item state and reflects the new favorite state in the list

#### Scenario: User toggles read-later from the updates list
- **WHEN** the user toggles read-later on a feed item in the RSS updates view
- **THEN** the Console updates the item state and reflects the new read-later state in the list

#### Scenario: User opens a read-later item
- **WHEN** the user opens the external article URL for a read-later feed item
- **THEN** the Console marks the item as read and removes it from read-later while preserving
  favorite state

#### Scenario: User filters saved item lists
- **WHEN** the user selects favorite or read-later filtering in the RSS updates view
- **THEN** the Console reloads the recent updates list with that saved-state filter
