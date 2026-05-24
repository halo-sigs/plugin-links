# link-rss-feed Specification

## Purpose
Define RSS/Atom tracking for friend links, including link-level configuration, lightweight status, embedded feed item storage, refresh flows, retention, and Console reading workflows.
## Requirements
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
- **WHEN** a user creates a link with RSS tracking enabled or enables RSS tracking on an existing link
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
- **THEN** it does not store feed item arrays, raw feed XML, article HTML, or article summaries in the `Link` resource

### Requirement: Embedded feed item store
The system SHALL store RSS/Atom feed items in a plugin-local embedded database instead of Halo Extension resources.

#### Scenario: Feed item is cached
- **WHEN** the system parses a feed item from an enabled link feed URL
- **THEN** it stores the item in the embedded feed item store with link name, feed URL, stable item ID, item URL, title, summary, author, published time, updated time, first-seen time, fetched time, and content hash

#### Scenario: Same item is refreshed again from the same feed URL
- **WHEN** a subsequent refresh returns a feed item with the same stable identity
  from the same link name and feed URL
- **THEN** the system updates the existing cached item instead of inserting a duplicate
- **AND** the system preserves the item's first-seen time while updating refreshed item data

#### Scenario: Same source-local identity appears in another feed URL
- **WHEN** two configured feed URLs under the same link return feed items with
  the same source-local stable identity
- **THEN** the system does not let one feed URL overwrite the other feed URL's
  cached item

#### Scenario: Large feed cache does not create Extension items
- **WHEN** the cached feed item count reaches 100000 records
- **THEN** the system still stores those records outside Halo Extension storage

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

### Requirement: Feed discovery
The system SHALL support discovering RSS/Atom feed URLs for a link website URL,
prioritizing Halo default feed paths before HTML link discovery.

#### Scenario: Halo default feed URLs are discovered first
- **WHEN** the user requests feed discovery for a website whose origin exposes a
  valid RSS or Atom document at `/rss.xml` or `/feed/moments/rss.xml`
- **THEN** the system returns the available Halo default feed URLs without
  enabling RSS automatically
- **AND** the system does not fetch or return HTML alternate-link discovery
  results for that request
- **AND** the system does not return a single `feedUrl` field

#### Scenario: Feed links are discovered from HTML after Halo defaults are unavailable
- **WHEN** the user requests feed discovery for a website that does not expose a
  valid RSS or Atom document at `/rss.xml` or `/feed/moments/rss.xml`
- **AND** the website exposes one or more RSS or Atom `<link>` elements
- **THEN** the system returns the discovered feed URLs without enabling RSS
  automatically
- **AND** the system does not return a single `feedUrl` field

#### Scenario: No feed is discovered
- **WHEN** the user requests feed discovery for a website without a valid Halo
  default feed URL or discoverable HTML feed link
- **THEN** the system returns an empty feed URL list and leaves the link RSS
  configuration unchanged

### Requirement: Manual feed refresh
The system SHALL allow users to manually refresh RSS for an enabled link from the Console.

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
- **THEN** the system does not fetch feeds for links whose `spec.rss.enabled` is absent or `false`

#### Scenario: Scheduled refresh skips links without feed URLs
- **WHEN** the scheduled RSS refresh runs
- **THEN** the system does not fetch feeds for enabled links whose
  `spec.rss.feedUrls` is absent, empty, or only blank

### Requirement: Cursor-based feed item listing
The system SHALL expose a Console API for listing cached feed items with cursor pagination.

#### Scenario: First page is listed
- **WHEN** the Console requests recent feed items with a limit
- **THEN** the system returns at most that many items ordered by published time descending and stable ID descending

#### Scenario: Next page is listed
- **WHEN** the Console requests feed items with a cursor containing the previous page boundary
- **THEN** the system returns items older than that cursor without repeating items from the previous page

#### Scenario: Items are filtered by link
- **WHEN** the Console requests feed items for a specific link
- **THEN** the system returns only cached items associated with that link

#### Scenario: Items are filtered by group
- **WHEN** the Console requests feed items for a specific link group
- **THEN** the system returns only cached items for links currently assigned to that group

#### Scenario: Items are filtered by read state
- **WHEN** the Console requests feed items by read or unread state
- **THEN** the system returns only cached items matching that read state

#### Scenario: Item read state is updated
- **WHEN** the Console marks a cached feed item as read or unread
- **THEN** the system persists that read state in the embedded feed item store

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

### Requirement: Feed item retention
The system SHALL enforce retention limits for cached RSS/Atom feed items using local cache lifecycle age for age-based cleanup.

#### Scenario: Per-link item limit is exceeded
- **WHEN** a link has more cached feed items than the configured per-link limit
- **THEN** the system deletes the oldest cached items for that link until the limit is satisfied

#### Scenario: Global item limit is exceeded
- **WHEN** the embedded feed item store contains more records than the configured global limit
- **THEN** the system deletes the oldest cached feed items until the limit is satisfied

#### Scenario: Age limit is exceeded
- **WHEN** an unsaved cached feed item's first-seen time is older than the configured retention age
- **THEN** the system deletes that cached feed item during retention cleanup

#### Scenario: Remote publication time is older than the age limit
- **WHEN** an unsaved cached feed item's remote published time is older than the configured retention age
- **AND** the item's first-seen time is within the configured retention age
- **THEN** age-based retention does not delete that cached feed item

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

### Requirement: Console RSS updates view
The system SHALL provide a Console view for reading recent RSS/Atom updates from friend links using a subscription sidebar and a primary article list.

#### Scenario: Recent updates are displayed
- **WHEN** the user opens the RSS updates view
- **THEN** the Console displays recent cached feed items with title, source link, publication time, summary, and external article URL

#### Scenario: Subscription sidebar is displayed
- **WHEN** the user opens the RSS updates view
- **THEN** the Console displays an "all updates" entry and subscribed links in a left-side subscription list
- **AND** subscribed link entries include only links with RSS tracking enabled
  and at least one feed URL configured

#### Scenario: Link with multiple feed URLs appears once
- **WHEN** a subscribed link has multiple configured feed URLs
- **THEN** the Console displays one subscription entry for that link

#### Scenario: Recent updates are filtered by subscription
- **WHEN** the user selects a subscribed link from the subscription list
- **THEN** the Console reloads the article list with that link selected as the link filter
- **AND** the article list includes cached items from all configured feed URLs
  under that link

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
- **THEN** the Console displays whether the latest refresh succeeded, failed, or
  partially failed across that link's configured feed URLs

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
