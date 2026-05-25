## ADDED Requirements

### Requirement: Bulk feed item mark-read API
The system SHALL expose a Console API operation that marks cached unread RSS/Atom feed
items as read without requiring the Console to provide individual item IDs.

#### Scenario: All unread items are marked read
- **WHEN** the Console submits the bulk mark-read operation without a link scope
- **THEN** the system marks every cached feed item whose read state is false as read
- **AND** the system returns the number of items updated

#### Scenario: Selected link unread items are marked read
- **WHEN** the Console submits the bulk mark-read operation with a link metadata name
- **THEN** the system marks every cached feed item for that link whose read state is false as read
- **AND** cached feed items for other links keep their existing read state
- **AND** the system returns the number of items updated

#### Scenario: Already read items are not counted
- **WHEN** the bulk mark-read operation matches cached feed items that are already read
- **THEN** those items remain read
- **AND** they are not included in the returned updated count

#### Scenario: Empty bulk mark-read scope is a no-op
- **WHEN** the bulk mark-read operation matches no unread cached feed items
- **THEN** the system does not create feed item records
- **AND** the system returns an updated count of 0

## MODIFIED Requirements

### Requirement: Console current-list mark-read action
The system SHALL allow Console users to mark all unread RSS/Atom feed items in the
primary friend-link feed list as read after confirmation, using a backend bulk operation
scoped to all subscriptions or the selected link subscription.

#### Scenario: All subscription unread items are marked read
- **WHEN** the all-updates subscription is selected
- **AND** the user activates the mark-all-read action
- **AND** the user confirms the action
- **THEN** the system marks all unread cached feed items across all subscriptions as read
- **AND** the system reloads the primary feed list using the active read-state filter

#### Scenario: Selected subscription unread items are marked read
- **WHEN** a link subscription is selected
- **AND** the user activates the mark-all-read action
- **AND** the user confirms the action
- **THEN** the system marks all unread cached feed items for that selected link as read
- **AND** cached feed items for other links keep their existing read state
- **AND** the system reloads the primary feed list using the active subscription and read-state filters

#### Scenario: Confirmation explains bulk scope
- **WHEN** the user activates the mark-all-read action
- **THEN** the system displays a confirmation dialog before updating read state
- **AND** the dialog identifies whether all subscriptions or the selected subscription will be affected
- **AND** the dialog does not describe the action as limited to currently loaded items

#### Scenario: No unread items are reported without error
- **WHEN** the user confirms the mark-all-read action
- **AND** the backend bulk operation updates 0 items
- **THEN** the Console reports that there are no unread items to mark as read for the chosen scope
- **AND** the system does not treat the result as a failed request
