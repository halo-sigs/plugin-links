## Context

The Console RSS updates view already has the right high-level layout: a subscription sidebar, a selected-source header, read-state tabs, and a primary article list. The current sidebar right-side slot is overloaded: the "全部动态" entry shows cached item count, while individual subscriptions show a feed health icon. Unread state lives in the embedded feed item store and can change frequently through per-item read toggles or the bulk mark-read action, while `Link.status.rss` stores refresh/cache health data for the link and configured feed URLs.

The unread count must be accurate across paginated items. The existing feed item list API can filter by `read=false`, but its cursor-paginated response does not return totals, so counting unread items in the sidebar from loaded pages would be incomplete.

## Goals / Non-Goals

**Goals:**

- Show unread counts for "全部动态" and each RSS subscription in the sidebar.
- Keep RSS health/status visible, but move it to the selected-source header where it can open details.
- Provide a feed status details modal that explains aggregate link status and per-feed URL state.
- Keep unread summary data lightweight and derived from the embedded feed item store.
- Keep the frontend data flow aligned with the existing Vue Query/generated-client pattern.

**Non-Goals:**

- Do not store unread counts on `Link.status.rss`.
- Do not create feed-level sidebar subscriptions; one `Link` remains one subscription even with multiple feed URLs.
- Do not change RSS refresh, retention, favorite, read-later, or per-item read-state behavior beyond refreshing the new summary data.
- Do not add group-level unread summaries unless requested separately.

## Decisions

### Add a dedicated unread summary API

Add a lightweight Console API operation that returns:

- total unread cached item count across all subscriptions
- per-link unread cached item count keyed by link metadata name

The backend should compute this from the embedded feed item store using read-state filters, not from `Link.status.rss`.

Alternative considered: derive unread counts in the frontend by requesting `rss/items?read=false` for each link. That would multiply requests by subscription count and still be either paginated/incomplete or forced to fetch too much data.

Alternative considered: add `unreadCount` to `Link.status.rss`. That couples high-frequency user reading state to the Halo Extension status resource and would require status updates whenever an item is marked read or unread.

### Keep status data in the selected-source header

The subscription sidebar should use its right-side slot for unread counts. RSS health moves to the selected RSS updates header next to the selected source title. When a single link is selected, the badge reflects that link's `status.rss`. When "全部动态" is selected, the header can show an aggregate state such as all healthy, partially unhealthy, or all waiting based on the loaded RSS-enabled links.

Alternative considered: show both unread count and health icon in every sidebar row. That makes the sidebar visually busier and weakens the count as the primary scanning signal.

### Open details from a small status badge

The status badge should be clickable and open a modal. For a selected link, the modal should show:

- link display name and configured feed URLs
- aggregate cached item count, unread count, last fetched time, last success time, latest published time, failure count, and last error
- per-feed URL last fetched time, last success time, latest published time, item count, failure count, and last error

For "全部动态", the modal should summarize counts across subscriptions and list subscriptions with warning or failure states first.

### Invalidate summary data after read-state and feed mutations

The unread summary query should be invalidated after:

- per-item read/unread toggles
- bulk mark-read completion
- manual current/all subscription refresh
- feed item cleanup

Favorite and read-later mutations do not need to change unread counts unless they also mark an item read through existing item-opening behavior.

## Risks / Trade-offs

- [Risk] Counting per-link unread items by scanning the embedded store could become expensive as the cache grows. -> Mitigation: implement store-level count helpers and avoid listing/parsing full item pages for the frontend.
- [Risk] Summary counts can briefly lag after a mutation if only the item list query is refreshed. -> Mitigation: centralize a `QK_LINK_FEED_SUMMARY` query key and invalidate it beside `QK_LINK_FEED_ITEMS`.
- [Risk] A hidden zero count may make it unclear why a subscription has no number. -> Mitigation: use hidden or muted zero counts consistently and keep the selected header meta explicit.
- [Risk] Aggregate "全部动态" status can become hard to interpret. -> Mitigation: keep aggregate text simple and make the detail modal show the exact subscriptions and feed URLs with warnings/failures.

## Migration Plan

1. Add backend summary models and store count helpers.
2. Add the Console summary endpoint and regenerate the frontend API client.
3. Add a Vue Query composable for unread summaries.
4. Update the subscription sidebar to render unread counts instead of feed health.
5. Add the selected-source status badge and details modal.
6. Invalidate unread summaries after read-state, refresh, and cleanup mutations.

No data migration is required because unread counts are derived from existing cached feed item state.
