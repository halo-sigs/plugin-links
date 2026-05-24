## ADDED Requirements

### Requirement: Feed page remote refresh
The system SHALL allow Console users to trigger remote RSS or Atom refreshes from
the friend-link feed page without confusing remote feed refresh with cache-only
item reload.

#### Scenario: Current subscription is refreshed from the feed page
- **WHEN** a user selects an RSS-enabled link on the friend-link feed page and triggers the current-subscription refresh action
- **THEN** the system refreshes the selected link's configured feed URLs through the manual feed refresh flow
- **AND** the system reloads the cached feed item list for the active filters after the remote refresh completes

#### Scenario: Current subscription refresh requires a selected subscription
- **WHEN** no RSS subscription is selected on the friend-link feed page
- **THEN** the system does not present an enabled current-subscription refresh action

#### Scenario: All subscriptions are refreshed from the feed page
- **WHEN** a user triggers the all-subscriptions refresh action on the friend-link feed page
- **THEN** the system attempts to refresh every RSS-enabled link currently listed as a subscription
- **AND** the system reloads the cached feed item list for the active filters after the remote refresh completes

#### Scenario: All-subscription refresh keeps partial success
- **WHEN** an all-subscriptions refresh encounters a failure for one subscription
- **THEN** the system continues attempting refreshes for the remaining subscriptions
- **AND** the system reports that at least one subscription failed without hiding successful refreshes

#### Scenario: Cache-only reload is clearly labeled
- **WHEN** the friend-link feed page provides an action that only reloads cached feed items without fetching remote feed URLs
- **THEN** the action label does not imply remote RSS or Atom refresh
