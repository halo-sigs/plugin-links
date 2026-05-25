## ADDED Requirements

### Requirement: Feed unread summary API
The system SHALL expose a lightweight Console API operation that reports unread RSS/Atom feed item counts without requiring the Console to load feed item pages.

#### Scenario: Aggregate unread count is returned
- **WHEN** the Console requests the RSS unread summary
- **THEN** the system returns the number of cached feed items whose read state is false across all RSS subscriptions

#### Scenario: Per-link unread counts are returned
- **WHEN** the Console requests the RSS unread summary
- **THEN** the system returns unread cached feed item counts keyed by link metadata name
- **AND** each per-link count includes only cached feed items associated with that link

#### Scenario: Read items are excluded
- **WHEN** cached feed items for a link include both read and unread items
- **THEN** the unread summary counts only cached feed items whose read state is false

#### Scenario: Missing unread items produce zero counts
- **WHEN** the unread summary does not include a per-link count for a subscribed link
- **THEN** the Console treats that link's unread count as zero

### Requirement: Console subscription unread counts
The Console RSS updates view SHALL use unread item counts as the primary right-side numeric signal in the subscription sidebar.

#### Scenario: All-updates entry shows aggregate unread count
- **WHEN** the user opens the RSS updates view
- **THEN** the "全部动态" subscription entry displays the aggregate unread count from the RSS unread summary
- **AND** the entry does not use total cached item count as its sidebar number

#### Scenario: Subscription entry shows link unread count
- **WHEN** the user opens the RSS updates view
- **AND** a subscribed link has unread cached feed items
- **THEN** that subscription entry displays the unread count for that link

#### Scenario: Subscription entry without unread items is quiet
- **WHEN** the user opens the RSS updates view
- **AND** a subscribed link has no unread cached feed items
- **THEN** that subscription entry does not show a prominent unread count

#### Scenario: Sidebar does not show per-link health badges
- **WHEN** the user opens the RSS updates view
- **THEN** subscribed link entries in the sidebar do not use the right-side slot for RSS refresh health badges

#### Scenario: Selected-source metadata keeps cache context
- **WHEN** a user selects a subscription or the all-updates entry
- **THEN** the selected RSS updates header continues to show cache context such as subscription count, feed URL count, cached item count, or unread count

### Requirement: Console RSS status details
The Console RSS updates view SHALL display RSS refresh health near the selected source title and allow users to inspect aggregate and per-feed status details.

#### Scenario: Selected link status badge is displayed in the header
- **WHEN** the user selects a subscribed link
- **THEN** the RSS updates header displays a status badge for that link based on the latest aggregate and per-feed RSS status
- **AND** the sidebar entry for that link remains focused on unread count

#### Scenario: All-updates status badge summarizes subscriptions
- **WHEN** the user selects the all-updates entry
- **THEN** the RSS updates header displays an aggregate status badge for the loaded RSS-enabled subscriptions

#### Scenario: Link status details are opened
- **WHEN** the user activates the status badge while a subscribed link is selected
- **THEN** the Console opens a details modal for that link
- **AND** the modal displays configured feed URLs, aggregate RSS status, cached item count, unread item count, last fetched time, last success time, latest published time, failure count, and last error when available

#### Scenario: Per-feed URL status details are shown
- **WHEN** the selected link has multiple configured feed URLs
- **AND** the user opens the status details modal
- **THEN** the modal displays each feed URL's last fetched time, last success time, latest published time, cached item count, failure count, and last error when available

#### Scenario: Aggregate status details are opened
- **WHEN** the user activates the status badge while the all-updates entry is selected
- **THEN** the Console opens a details modal summarizing subscription count, cached item count, unread item count, and subscriptions with warning or failure states

#### Scenario: Status details handle waiting subscriptions
- **WHEN** a subscription has RSS enabled but no successful refresh status yet
- **THEN** the status badge and details modal represent that subscription as waiting rather than failed
