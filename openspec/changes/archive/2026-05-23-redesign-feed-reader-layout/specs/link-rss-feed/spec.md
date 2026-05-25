## MODIFIED Requirements

### Requirement: Console RSS updates view
The system SHALL provide a Console view for reading recent RSS/Atom updates from friend links using a subscription sidebar and a primary article list.

#### Scenario: Recent updates are displayed
- **WHEN** the user opens the RSS updates view
- **THEN** the Console displays recent cached feed items with title, source link, publication time, summary, and external article URL

#### Scenario: Subscription sidebar is displayed
- **WHEN** the user opens the RSS updates view
- **THEN** the Console displays an "all updates" entry and subscribed links in a left-side subscription list
- **AND** subscribed link entries include only links with RSS tracking enabled and a feed URL configured

#### Scenario: Recent updates are filtered by subscription
- **WHEN** the user selects a subscribed link from the subscription list
- **THEN** the Console reloads the article list with that link selected as the link filter

#### Scenario: All updates are selected
- **WHEN** the user selects the all-updates entry from the subscription list
- **THEN** the Console clears the link filter and reloads the article list across all subscribed links

#### Scenario: Recent updates are filtered by read state
- **WHEN** the user selects all, unread, or read from the read-state tabs above the article list
- **THEN** the Console reloads the recent updates list with that read-state filter

#### Scenario: Group filtering is not exposed
- **WHEN** the user opens the RSS updates view
- **THEN** the Console does not display a group filter in the RSS updates browsing controls

#### Scenario: Feed status is visible on a link
- **WHEN** a link has RSS tracking configured
- **THEN** the Console displays whether the latest refresh succeeded or failed

#### Scenario: Item text is rendered safely
- **WHEN** a feed item title or summary contains HTML markup
- **THEN** the Console renders it as sanitized or plain text content

#### Scenario: Reader layout adapts to narrow viewports
- **WHEN** the Console viewport cannot comfortably fit the subscription list and article list side by side
- **THEN** the Console keeps subscription selection and article browsing usable without overlapping controls or text

### Requirement: Console saved item workflows
The system SHALL expose favorite and read-later as independent saved-item workflows in the Console RSS updates view without interrupting the primary subscription-and-read-state article browsing flow.

#### Scenario: User toggles favorite from the updates list
- **WHEN** the user toggles favorite on a feed item in the RSS updates view
- **THEN** the Console updates the item state and reflects the new favorite state in the list

#### Scenario: User opens the favorites list
- **WHEN** the user activates the favorite entry from the RSS updates page header
- **THEN** the Console displays favorite feed items in a separate list without replacing the main RSS updates list

#### Scenario: User toggles read-later from the updates list
- **WHEN** the user toggles read-later on a feed item in the RSS updates view
- **THEN** the Console updates the item state and reflects the new read-later state in the list

#### Scenario: User views read-later items
- **WHEN** the user activates a read-later entry from the RSS updates view
- **THEN** the Console displays read-later feed items in a separate list without inserting the read-later list above the primary article list

#### Scenario: User opens a read-later item
- **WHEN** the user opens the external article URL for a read-later feed item
- **THEN** the Console marks the item as read and removes it from read-later while preserving favorite state

#### Scenario: Primary feed controls exclude saved-state and group controls
- **WHEN** the user views the primary RSS updates browsing controls
- **THEN** the Console offers subscription selection and read-state tabs without group, favorite, or read-later dropdown filters
