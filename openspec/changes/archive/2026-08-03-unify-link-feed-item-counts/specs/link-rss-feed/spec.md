## ADDED Requirements

### Requirement: Console RSS item count summary API
The system SHALL expose a read-only Console API operation at
`GET /apis/console.api.link.halo.run/v1alpha1/rss/items/-/summary` that returns the exact global
counts for hidden, favorite, and read-later cached RSS/Atom feed items without loading item pages.

#### Scenario: Summary returns all three counts
- **WHEN** an authorized Console client requests the RSS item count summary
- **THEN** the response contains `hiddenCount`, `favoriteCount`, and `readLaterCount`
- **AND** all three fields are numeric, including when their value is zero

#### Scenario: Hidden count includes every hidden item
- **WHEN** cached feed items include hidden items with different read, favorite, and read-later states
- **THEN** `hiddenCount` counts every item whose hidden state is true
- **AND** it does not depend on the item's read, favorite, or read-later state

#### Scenario: Saved counts include visible items only
- **WHEN** cached feed items include visible and hidden favorite or read-later items
- **THEN** `favoriteCount` counts only items whose hidden and favorite states are false and true
- **AND** `readLaterCount` counts only items whose hidden and read-later states are false and true
- **AND** a visible item marked both favorite and read-later contributes independently to both counts

#### Scenario: Summary is global
- **WHEN** the Console requests the item count summary while a subscription or read-state filter is
  selected in the primary feed view
- **THEN** the response counts all cached feed items across all subscriptions
- **AND** it does not apply the selected subscription or read-state filter

#### Scenario: Summary requires view permission
- **WHEN** a user with `plugin:links:view` requests the item count summary
- **THEN** the request is authorized
- **AND** the operation does not require `plugin:links:manage`

## MODIFIED Requirements

### Requirement: Hidden feed item Console queries
The system SHALL let authenticated Console clients list hidden or visible cached feed items with
cursor pagination and obtain exact global hidden, favorite, and read-later counts from the shared RSS
item count summary.

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
- **WHEN** an authorized Console client requests the shared RSS item count summary
- **THEN** the system returns exact global `hiddenCount`, `favoriteCount`, and `readLaterCount` values
- **AND** `hiddenCount` includes every hidden cached item while the saved counts exclude hidden items
- **AND** it does not require loading a feed item page

### Requirement: Console saved item workflows
The system SHALL expose favorite and read-later as independent saved-item workflows in the Console RSS
updates view without interrupting the primary subscription-and-read-state article browsing flow, and
their header entries SHALL display exact global counts of visible saved items.

#### Scenario: User toggles favorite from the updates list
- **WHEN** the user toggles favorite on a feed item in the RSS updates view
- **THEN** the Console updates the item state and reflects the new favorite state in the list
- **AND** it refreshes the item count summary after the update succeeds

#### Scenario: User opens the favorites list
- **WHEN** the user activates the favorite entry from the RSS updates page header
- **THEN** the Console displays favorite feed items in a separate list without replacing the main RSS
  updates list
- **AND** the header entry displays the exact global count of non-hidden favorite items

#### Scenario: Saved-item counts stay stable while loading
- **WHEN** the shared RSS item count summary is initially loading or refetching
- **THEN** the read-later, favorite, and hidden header entries each display `(0)`
- **AND** the entries remain usable without changing their layout when the summary resolves

#### Scenario: User toggles read-later from the updates list
- **WHEN** the user toggles read-later on a feed item in the RSS updates view
- **THEN** the Console updates the item state and reflects the new read-later state in the list
- **AND** it refreshes the item count summary after the update succeeds

#### Scenario: User views read-later items
- **WHEN** the user activates a read-later entry from the RSS updates view
- **THEN** the Console displays read-later feed items in a separate list without inserting the read-later
  list above the primary article list
- **AND** the header entry displays the exact global count of non-hidden read-later items

#### Scenario: User opens a read-later item
- **WHEN** the user opens the external article URL for a read-later feed item
- **THEN** the Console marks the item as read and removes it from read-later while preserving favorite
  state
- **AND** it refreshes the item count summary after the read-later state is removed

#### Scenario: Saved entries exclude hidden items
- **WHEN** the user views the favorite or read-later entry and matching items include hidden rows
- **THEN** the displayed count and opened list exclude the hidden rows
- **AND** the hidden entry remains the separate workflow for those rows

#### Scenario: Primary feed controls exclude saved-state and group controls
- **WHEN** the user views the primary RSS updates browsing controls
- **THEN** the Console offers subscription selection and read-state tabs without group, favorite, or
  read-later dropdown filters

### Requirement: Console hidden feed item workflow
The Console RSS updates view SHALL provide focused single-item and explicit-selection workflows for
hiding and restoring cached feed items, and its header entry SHALL display the exact hidden count from
the shared RSS item count summary.

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
- **WHEN** the user changes subscription or read filters, reloads data, or completes a successful hide or
  unhide selection operation
- **THEN** the Console clears the applicable current selection

#### Scenario: User opens hidden items
- **WHEN** the user activates the "已隐藏" entry in the RSS updates page header
- **THEN** the Console opens a separate cursor-loaded list of hidden items
- **AND** the entry displays the exact `hiddenCount` from the shared RSS item count summary

#### Scenario: Hidden list keeps focused actions
- **WHEN** the Console displays a hidden feed item
- **THEN** it displays existing read, favorite, and read-later states without offering state edits
- **AND** it offers external open and single-item unhide actions

#### Scenario: User opens a hidden item article
- **WHEN** the user opens the external article URL from the hidden-item list
- **THEN** the Console keeps the item hidden, marks it read, removes it from read-later, and preserves
  favorite state
- **AND** it refreshes the item count summary after the read-later state is removed

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
  shared RSS item count summary
- **AND** it reports success without duplicating Halo's global HTTP failure feedback
