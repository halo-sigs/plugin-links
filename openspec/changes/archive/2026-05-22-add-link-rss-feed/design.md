## Context

The plugin currently stores friend links as Halo `Link` Extension resources and exposes a lightweight Console UI for link and group management. RSS/Atom tracking changes the data profile: one link can produce many feed items over time, and a site with many links can easily reach tens or hundreds of thousands of cached article records.

Halo Extension storage remains the right place for user-owned link configuration and small status fields, but it is not a good fit for high-volume feed item history. This change follows a storage split where Extension resources keep low-cardinality state, while a plugin-local embedded database stores operational records.

## Goals / Non-Goals

**Goals:**

- Add optional RSS/Atom tracking to each friend link.
- Keep `Link.spec.rss` as the source of truth for user configuration.
- Store lightweight fetch state in `Link.status.rss` instead of introducing a separate one-to-one status Extension.
- Store feed items in an embedded database collection, not in Extension status/spec.
- Provide cursor-based Console APIs for listing recent feed items at large scale.
- Protect all feed discovery and fetch requests with SSRF validation.
- Include retention and cleanup behavior from the first implementation.

**Non-Goals:**

- No public theme feed page in the first version.
- No full RSS reader behavior such as folders, starring, or per-user timelines.
- No permanent archival guarantee for feed items; they are treated as rebuildable cache.
- No migration from existing RSS data, because the plugin has no current RSS storage.
- No automatic background crawling of every linked website unless RSS is enabled for that link.

## Decisions

### 1. Store RSS configuration on `Link.spec.rss`

Add a nested RSS configuration object to `Link.spec`:

```text
spec.rss.enabled
spec.rss.feedUrl
```

**Rationale**: RSS tracking is a per-link user choice. Keeping the setting on the `Link` resource preserves the current mental model: users edit a link and decide whether that link contributes updates.

**Alternative considered**: Create a separate `LinkFeedSubscription` Extension. This would make sense for many subscriptions per link, but the first version only needs a one-to-one relationship and would add unnecessary resource management.

### 2. Store lightweight runtime state on `Link.status.rss`

Add status fields such as:

```text
status.rss.effectiveFeedUrl
status.rss.lastFetchedAt
status.rss.lastSuccessAt
status.rss.lastError
status.rss.failureCount
status.rss.etag
status.rss.lastModified
status.rss.latestPublishedAt
status.rss.itemCount
```

**Rationale**: This status is naturally one-to-one with the link and remains small. It gives users enough visibility for "is this feed working?" without creating a separate status resource.

**Guardrail**: `status.rss` MUST NOT contain feed item arrays, raw feed XML, or article content. Only scalar runtime state belongs there.

### 3. Store feed items in Nitrite

Add a shared embedded database component for the Links plugin, with a file such as:

```text
<plugins-root>/links/links.nitrite
```

Create a `link-feed-items` collection with fields:

```text
id
linkName
feedUrl
guid
url
title
summary
author
publishedAt
updatedAt
fetchedAt
contentHash
```

The `id` should be deterministic, for example a hash of `linkName` plus the feed entry guid or canonical URL. Fetching the same feed repeatedly then becomes an upsert operation.

**Rationale**: RSS item volume is unbounded relative to Extension expectations. Nitrite provides local file-backed persistence, indexed document queries, and upsert-style writes without introducing an external service.

**Alternative considered**: Store feed items as Extension resources. This keeps all data in Halo's native storage but risks slowing list/watch/index operations when the cache reaches high cardinality.

### 4. Use cursor pagination for feed item listing

Console listing APIs should use cursor pagination based on `publishedAt` plus `id`, for example:

```text
GET /apis/console.api.link.halo.run/v1alpha1/rss/items?limit=30&beforePublishedAt=...&beforeId=...
```

**Rationale**: Offset pagination becomes expensive and less stable for large caches. RSS item listing must avoid any path that requires reading large filtered result sets into memory for pagination.

### 5. Throttle Extension status writes

The fetcher should only update `Link.status.rss` when useful state changes:

- feed URL discovery result changes
- success/failure state changes
- ETag or Last-Modified changes
- latest published item changes
- item count changes after retention or new inserts

`lastFetchedAt` can be written on successful refreshes, failures, or at a coarse interval instead of every no-op poll.

**Rationale**: Even small status fields become write pressure if every enabled link writes on every polling round.

### 6. Treat feed items as cache with retention

The embedded store should enforce retention by count and age, with plugin settings such as:

```text
rss.maxItemsTotal
rss.maxItemsPerLink
rss.retentionDays
rss.refreshIntervalMinutes
```

**Rationale**: Feed items are discoverable again from upstream feeds only while upstream sources still publish them, but the plugin should not grow forever. Users need predictable disk usage.

### 7. Reuse and extend URL safety controls

Feed discovery and feed fetching must use the same URL safety posture as link metadata scraping: only HTTP/HTTPS URLs, reject private/reserved addresses, control redirects, and bound response size/time.

**Rationale**: RSS fetches are server-side network requests controlled by administrator input and remote documents. Without the existing SSRF boundary, feeds can become a private-network probe.

## Risks / Trade-offs

- **[Risk] Nitrite database corruption after abrupt shutdown or plugin upgrade**
  -> **Mitigation**: Use a shared database component with scheduled JSON export backups, shutdown backup, startup recovery, and compact/rebuild behavior.
- **[Risk] Feed parsing libraries may accept huge or malformed feeds**
  -> **Mitigation**: Enforce response timeout, maximum response size, XML parser hardening, item count limits per fetch, and summary/content truncation.
- **[Risk] Frequent refreshes can produce Extension write pressure through `status.rss`**
  -> **Mitigation**: Throttle status writes and avoid writing on no-op fetches unless the visible state changes.
- **[Risk] Cursor ordering can be unstable when many items share the same published time**
  -> **Mitigation**: Use `(publishedAt, id)` as the cursor pair and require deterministic item IDs.
- **[Risk] Group filtering can become expensive if implemented by in-memory filtering**
  -> **Mitigation**: Resolve current link names for a group from Extension storage, then query feed items by indexed `linkName` plus cursor constraints.
- **[Risk] Cached summaries could contain unsafe HTML**
  -> **Mitigation**: Store plain text summaries or sanitize before persistence, and render item text as text in the Console.

## Migration Plan

1. Add RSS fields to `Link.spec` and `Link.status`, defaulting RSS tracking to disabled for existing links.
2. Add embedded database dependencies and a shared Links Nitrite database component.
3. Add store interfaces and Nitrite implementations for feed item writes, cursor listing, and retention cleanup.
4. Add feed discovery/fetch services that reuse SSRF validation and update `Link.status.rss`.
5. Add Console endpoints and regenerate OpenAPI clients.
6. Add Console UI for link RSS settings and recent updates.
7. Add scheduled refresh and retention cleanup after manual refresh and list APIs are stable.

Rollback is straightforward for data correctness: disabling the feature stops refreshes, `spec.rss` remains inert metadata, and the embedded cache file can be removed if users want to reclaim disk space.

## Open Questions

- Should automatic feed discovery run only when users click "detect feed", or also after entering a website URL?
- Should the first version expose global RSS settings in plugin settings, or hard-code conservative defaults and add settings later?
- Should feed items be available to themes later through a Finder API, or remain Console-only?
