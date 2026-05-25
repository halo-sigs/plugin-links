## MODIFIED Requirements

### Requirement: Console saved item workflows
The system SHALL expose favorite and read-later as independent saved-item workflows in the
Console RSS updates view.

#### Scenario: User toggles favorite from the updates list
- **WHEN** the user toggles favorite on a feed item in the RSS updates view
- **THEN** the Console updates the item state and reflects the new favorite state in the list

#### Scenario: User opens the favorites list
- **WHEN** the user activates the favorite entry from the RSS updates page header
- **THEN** the Console displays favorite feed items in a separate list without replacing the
  main RSS updates list

#### Scenario: User toggles read-later from the updates list
- **WHEN** the user toggles read-later on a feed item in the RSS updates view
- **THEN** the Console updates the item state and reflects the new read-later state in the list

#### Scenario: User views read-later items
- **WHEN** the user opens the RSS updates view and read-later feed items exist
- **THEN** the Console displays a read-later list separately from the main RSS updates list

#### Scenario: User opens a read-later item
- **WHEN** the user opens the external article URL for a read-later feed item
- **THEN** the Console marks the item as read and removes it from read-later while preserving
  favorite state

#### Scenario: Primary feed filters exclude saved-state controls
- **WHEN** the user views the primary RSS updates filter bar
- **THEN** the Console offers link, group, and read-state filters without favorite or
  read-later dropdown filters
