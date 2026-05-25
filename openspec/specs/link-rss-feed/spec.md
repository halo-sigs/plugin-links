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
- **AND** the Console does not wait for the initial RSS refresh to complete before completing the save modal flow

#### Scenario: Initial RSS refresh failure does not block saved link
- **WHEN** the initial RSS refresh started after a successful link save fails
- **THEN** the saved `Link` remains persisted with its RSS configuration
- **AND** the Console does not reopen or keep open the save modal because of the RSS refresh failure

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

### Requirement: RSS conditional validators expire
The system SHALL periodically force a full RSS/Atom fetch for each configured
feed URL instead of trusting conditional request validators forever.

#### Scenario: Fresh validators are sent with cached items
- **WHEN** an enabled link feed URL has cached items
- **AND** its per-feed RSS status contains `ETag` or `Last-Modified` validators
- **AND** the validators have freshness metadata newer than the validator freshness window
- **THEN** the refresh request includes the usable conditional request headers

#### Scenario: Empty local cache skips validators
- **WHEN** an enabled link feed URL has no cached items
- **AND** its per-feed RSS status contains `ETag` or `Last-Modified` validators
- **THEN** the refresh request does not include `If-None-Match` or `If-Modified-Since`

#### Scenario: Missing freshness metadata skips validators
- **WHEN** an enabled link feed URL has cached items
- **AND** its per-feed RSS status contains `ETag` or `Last-Modified` validators
- **AND** the validators do not have freshness metadata
- **THEN** the refresh request does not include `If-None-Match` or `If-Modified-Since`

#### Scenario: Stale validators are not sent
- **WHEN** an enabled link feed URL has cached items
- **AND** its per-feed RSS status contains `ETag` or `Last-Modified` validators
- **AND** the validators are older than the validator freshness window
- **THEN** the refresh request does not include `If-None-Match` or `If-Modified-Since`

#### Scenario: Real feed response refreshes validator freshness
- **WHEN** a feed URL refresh receives a successful response with a feed body
- **AND** the response contains `ETag` or `Last-Modified` validators
- **THEN** the system stores those validators on the per-feed RSS status
- **AND** the system records the validator freshness time for that feed URL

#### Scenario: Not-modified response preserves validator freshness
- **WHEN** a feed URL refresh receives `304 Not Modified`
- **THEN** the system preserves the previous per-feed validators
- **AND** the system preserves the previous validator freshness time

#### Scenario: Failed refresh preserves validator freshness
- **WHEN** a feed URL refresh fails
- **THEN** the system preserves the previous per-feed validators
- **AND** the system preserves the previous validator freshness time

#### Scenario: Invalid Last-Modified is not sent
- **WHEN** an enabled link feed URL has cached items
- **AND** its stored `Last-Modified` validator contains an invalid 2038 timestamp
- **THEN** the refresh request does not include `If-Modified-Since`

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

### Requirement: Link deletion feed cache cleanup
The system SHALL remove cached RSS/Atom feed items that belong to a `Link` when that `Link` is deleted.

#### Scenario: Deleted link cache is removed
- **WHEN** a `Link` enters deletion and RSS/Atom feed items are cached with that link's metadata name
- **THEN** the system removes those cached feed items before the `Link` deletion is finalized

#### Scenario: Saved items do not survive link deletion
- **WHEN** a deleted `Link` has cached RSS/Atom feed items marked as read, favorite, or read-later
- **THEN** the system removes those cached feed items together with the rest of that link's feed cache

#### Scenario: Link without cached feed items is deleted
- **WHEN** a `Link` enters deletion and no RSS/Atom feed items are cached with that link's metadata name
- **THEN** the system finalizes the `Link` deletion without creating feed item records

#### Scenario: Normal retention still protects saved items
- **WHEN** normal RSS/Atom cache retention cleanup runs for links that are not being deleted
- **THEN** the system continues to protect cached feed items whose favorite or read-later state is true

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
