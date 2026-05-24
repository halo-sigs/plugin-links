## ADDED Requirements

### Requirement: Console current-list mark-read action
The system SHALL allow Console users to mark currently loaded unread RSS/Atom feed items in
the primary friend-link feed list as read after confirmation.

#### Scenario: Loaded unread items are marked read
- **WHEN** the primary friend-link feed list contains currently loaded unread items
- **AND** the user activates the mark-all-read action
- **AND** the user confirms the action
- **THEN** the system marks each currently loaded unread item in the primary list as read
- **AND** the system reloads the primary feed list using the active subscription and read-state filters

#### Scenario: Confirmation explains affected items
- **WHEN** the user activates the mark-all-read action with one or more currently loaded unread items
- **THEN** the system displays a confirmation dialog before updating read state
- **AND** the dialog states how many currently loaded unread items will be marked as read
- **AND** the dialog communicates that unloaded older items are outside the action scope

#### Scenario: Action is unavailable without loaded unread items
- **WHEN** the primary friend-link feed list has no currently loaded unread items
- **THEN** the system does not submit mark-read requests for the mark-all-read action

#### Scenario: Unloaded items are not marked read
- **WHEN** the active feed query has unread items that have not been loaded into the current list
- **AND** the user confirms the mark-all-read action
- **THEN** the system does not mark those unloaded items as read
