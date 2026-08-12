## ADDED Requirements

### Requirement: RSS unsubscribe data cleanup
The system SHALL remove operational RSS data owned by a Link when that Link is no longer configured
as an enabled RSS subscription.

#### Scenario: User disables an enabled subscription
- **WHEN** a Link with `spec.rss.enabled` set to `true` is updated so that
  `spec.rss.enabled` is `false`
- **THEN** the system excludes that Link from subsequent RSS refreshes
- **AND** the system removes every cached feed item whose link name matches that Link
- **AND** the system clears the Link's `status.rss`
- **AND** the system preserves the Link's configured `spec.rss.feedUrls`

#### Scenario: RSS configuration is removed
- **WHEN** an enabled Link's `spec.rss` configuration is removed
- **THEN** the system removes every cached feed item whose link name matches that Link
- **AND** the system clears the Link's `status.rss`

#### Scenario: Saved and hidden items do not survive unsubscribe
- **WHEN** an unsubscribed Link owns cached feed items marked as read, favorite, read-later, or hidden
- **THEN** the system removes those items together with the rest of that Link's feed cache

#### Scenario: Existing unsubscribed Link has stale data
- **WHEN** startup reconciliation observes a Link that is not an enabled RSS subscription and that
  Link still has cached feed items or `status.rss`
- **THEN** the system removes the stale cached items and clears `status.rss`

#### Scenario: Unsubscribe cleanup fails
- **WHEN** the feed item store fails while cleaning an unsubscribed Link
- **THEN** the system does not clear the Link's `status.rss` as though cleanup succeeded
- **AND** a later reconciliation can retry the idempotent cleanup

#### Scenario: Refresh completes after unsubscribe
- **WHEN** an RSS refresh begins while a Link is enabled and the Link is unsubscribed before that
  refresh completes
- **THEN** the refresh does not leave cached feed items for that Link
- **AND** the refresh does not restore `status.rss`

#### Scenario: Subscription is enabled again
- **WHEN** a previously unsubscribed Link is enabled again with one or more feed URLs
- **THEN** the existing initial refresh flow fetches the subscription into an empty Link cache
- **AND** the system derives new RSS runtime status from that refresh

### Requirement: Console RSS unsubscribe confirmation
The Console SHALL warn administrators before saving a Link change that ends an enabled RSS
subscription.

#### Scenario: Administrator confirms unsubscribe
- **WHEN** an administrator saves a Link after changing RSS from enabled to disabled
- **THEN** the Console asks for confirmation that all cached RSS items, including saved and hidden
  items, will be permanently removed
- **AND** after confirmation the Console submits the Link update
- **AND** after a successful update the Console invalidates affected Link, feed item, unread, and
  item-summary queries

#### Scenario: Administrator cancels unsubscribe
- **WHEN** an administrator cancels the RSS unsubscribe confirmation
- **THEN** the Console does not submit the Link update

#### Scenario: Administrator edits an already disabled subscription
- **WHEN** an administrator saves other changes to a Link whose RSS subscription was already disabled
- **THEN** the Console does not show the unsubscribe confirmation
