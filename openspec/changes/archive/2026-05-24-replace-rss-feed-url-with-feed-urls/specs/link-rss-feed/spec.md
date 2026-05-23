## MODIFIED Requirements

### Requirement: Link RSS configuration
The system SHALL allow each `Link` to optionally configure RSS/Atom tracking
through `spec.rss` with a Link-level enable switch and a list of feed URLs.

#### Scenario: RSS is disabled by default
- **WHEN** an existing link has no `spec.rss` value
- **THEN** the system treats RSS tracking as disabled for that link

#### Scenario: User enables RSS tracking with feed URLs
- **WHEN** a user saves a link with `spec.rss.enabled` set to `true` and
  `spec.rss.feedUrls` containing one or more absolute HTTP or HTTPS URLs
- **THEN** the system persists the RSS configuration on the `Link` resource
- **AND** the system does not persist `spec.rss.feedUrl`

#### Scenario: Enabled RSS requires at least one feed URL
- **WHEN** a user saves a link with `spec.rss.enabled` set to `true` and
  `spec.rss.feedUrls` absent, empty, or containing only blank values
- **THEN** the system rejects the RSS configuration without enabling RSS tracking

#### Scenario: User enables RSS tracking for the first time
- **WHEN** a user creates a link with RSS tracking enabled or enables RSS
  tracking on an existing link
- **THEN** the Console starts an initial RSS refresh after saving the link

#### Scenario: User disables RSS tracking
- **WHEN** a user sets `spec.rss.enabled` to `false`
- **THEN** the system excludes that link from scheduled RSS refreshes

#### Scenario: Feed URLs do not create feed-level subscriptions
- **WHEN** a link has multiple values in `spec.rss.feedUrls`
- **THEN** the system treats the link as one RSS subscription
- **AND** the system does not require per-feed enablement, names, groups, or
  sidebar identities

### Requirement: Link RSS status
The system SHALL store lightweight aggregate RSS runtime state and per-feed
runtime state on `Link.status.rss`.

#### Scenario: Feed fetch succeeds
- **WHEN** the system successfully refreshes one configured feed URL for a link
- **THEN** it updates the matching per-feed status with the feed URL, latest
  success time, conditional request metadata, latest published item time, and
  item count
- **AND** it updates aggregate `status.rss` state for the owning link, including
  latest fetch time, latest success time, latest published item time, and total
  cached item count

#### Scenario: Feed fetch fails
- **WHEN** the system fails to refresh one configured feed URL for a link
- **THEN** it records the failure message and failure count on that feed URL's
  per-feed status
- **AND** it does not overwrite successful status for the link's other feed URLs

#### Scenario: Removed feed URL status is not retained
- **WHEN** a feed URL is removed from `spec.rss.feedUrls`
- **THEN** subsequent RSS status updates do not retain runtime status for that
  removed feed URL

#### Scenario: Feed item content is not stored in status
- **WHEN** the system updates `status.rss`
- **THEN** it does not store feed item arrays, raw feed XML, article HTML, or
  article summaries in the `Link` resource

### Requirement: Embedded feed item store
The system SHALL store RSS/Atom feed items in a plugin-local embedded database
instead of Halo Extension resources.

#### Scenario: Feed item is cached
- **WHEN** the system parses a feed item from an enabled link feed URL
- **THEN** it stores the item in the embedded feed item store with link name,
  feed URL, stable item ID, item URL, title, summary, author, published time,
  updated time, fetched time, and content hash

#### Scenario: Same item is refreshed again from the same feed URL
- **WHEN** a subsequent refresh returns a feed item with the same stable identity
  from the same link name and feed URL
- **THEN** the system updates the existing cached item instead of inserting a
  duplicate

#### Scenario: Same source-local identity appears in another feed URL
- **WHEN** two configured feed URLs under the same link return feed items with
  the same source-local stable identity
- **THEN** the system does not let one feed URL overwrite the other feed URL's
  cached item

#### Scenario: Large feed cache does not create Extension items
- **WHEN** the cached feed item count reaches 100000 records
- **THEN** the system still stores those records outside Halo Extension storage

### Requirement: Feed discovery
The system SHALL support discovering RSS/Atom feed URLs for a link website URL.

#### Scenario: Feed links are discovered from HTML
- **WHEN** the user requests feed discovery for a website that exposes one or
  more RSS or Atom `<link>` elements
- **THEN** the system returns the discovered feed URLs without enabling RSS
  automatically
- **AND** the system does not return a single `feedUrl` field

#### Scenario: No feed is discovered
- **WHEN** the user requests feed discovery for a website without discoverable
  feeds
- **THEN** the system returns an empty feed URL list and leaves the link RSS
  configuration unchanged

### Requirement: Manual feed refresh
The system SHALL allow users to manually refresh RSS for an enabled link from
the Console.

#### Scenario: Manual refresh inserts new items
- **WHEN** a user manually refreshes a link with RSS enabled and one or more
  configured feed URLs
- **THEN** the system fetches each configured feed URL, caches new feed items,
  and updates `status.rss`

#### Scenario: Manual refresh isolates feed URL failures
- **WHEN** a user manually refreshes a link with multiple configured feed URLs
  and one feed URL fails
- **THEN** the system records failure status for the failed feed URL
- **AND** the system still caches and reports successful results from the other
  feed URLs

#### Scenario: Manual refresh rejects disabled link
- **WHEN** a user manually refreshes a link with RSS disabled
- **THEN** the system rejects the refresh request without fetching any feed URL

#### Scenario: Manual refresh rejects enabled link without feed URLs
- **WHEN** a user manually refreshes a link with RSS enabled but no configured
  feed URLs
- **THEN** the system rejects the refresh request without fetching any feed URL

### Requirement: Scheduled feed refresh
The system SHALL refresh enabled link feeds on a background schedule.

#### Scenario: Scheduled refresh processes enabled links
- **WHEN** the scheduled RSS refresh runs
- **THEN** the system refreshes links whose `spec.rss.enabled` is `true` and
  whose `spec.rss.feedUrls` contains at least one feed URL

#### Scenario: Scheduled refresh processes every configured feed URL
- **WHEN** the scheduled RSS refresh processes an enabled link with multiple
  configured feed URLs
- **THEN** the system attempts to refresh each configured feed URL for that link

#### Scenario: Scheduled refresh skips disabled links
- **WHEN** the scheduled RSS refresh runs
- **THEN** the system does not fetch feeds for links whose `spec.rss.enabled` is
  absent or `false`

#### Scenario: Scheduled refresh skips links without feed URLs
- **WHEN** the scheduled RSS refresh runs
- **THEN** the system does not fetch feeds for enabled links whose
  `spec.rss.feedUrls` is absent, empty, or only blank

### Requirement: Console RSS updates view
The system SHALL provide a Console view for reading recent RSS/Atom updates from
friend links using a subscription sidebar and a primary article list.

#### Scenario: Recent updates are displayed
- **WHEN** the user opens the RSS updates view
- **THEN** the Console displays recent cached feed items with title, source link,
  publication time, summary, and external article URL

#### Scenario: Subscription sidebar is displayed
- **WHEN** the user opens the RSS updates view
- **THEN** the Console displays an "all updates" entry and subscribed links in a
  left-side subscription list
- **AND** subscribed link entries include only links with RSS tracking enabled
  and at least one feed URL configured

#### Scenario: Link with multiple feed URLs appears once
- **WHEN** a subscribed link has multiple configured feed URLs
- **THEN** the Console displays one subscription entry for that link

#### Scenario: Recent updates are filtered by subscription
- **WHEN** the user selects a subscribed link from the subscription list
- **THEN** the Console reloads the article list with that link selected as the
  link filter
- **AND** the article list includes cached items from all configured feed URLs
  under that link

#### Scenario: All updates are selected
- **WHEN** the user selects the all-updates entry from the subscription list
- **THEN** the Console clears the link filter and reloads the article list
  across all subscribed links

#### Scenario: Recent updates are filtered by read state
- **WHEN** the user selects all, unread, or read from the read-state tabs above
  the article list
- **THEN** the Console reloads the recent updates list with that read-state
  filter

#### Scenario: Group filtering is not exposed
- **WHEN** the user opens the RSS updates view
- **THEN** the Console does not display a group filter in the RSS updates
  browsing controls

#### Scenario: Feed status is visible on a link
- **WHEN** a link has RSS tracking configured
- **THEN** the Console displays whether the latest refresh succeeded, failed, or
  partially failed across that link's configured feed URLs

#### Scenario: Item text is rendered safely
- **WHEN** a feed item title or summary contains HTML markup
- **THEN** the Console renders it as sanitized or plain text content

#### Scenario: Reader layout adapts to narrow viewports
- **WHEN** the Console viewport cannot comfortably fit the subscription list and
  article list side by side
- **THEN** the Console keeps subscription selection and article browsing usable
  without overlapping controls or text
