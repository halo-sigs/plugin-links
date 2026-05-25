## Context

The create and edit modals save the `Link` first, then conditionally call
`refreshLinkFeed` when RSS is enabled and feed URLs are present. That remote RSS fetch can take
noticeable time because it depends on external sites, and the current modal success handler waits
for the fetch before closing and invalidating queries.

The backend refresh API already encapsulates feed fetching, item caching, status updates, failure
recording, SSRF protections, and retention cleanup. Manual refresh actions on the RSS feed page
intentionally wait for that API so the visible feed list can reload after the remote fetch. The
slow-save problem only affects the initial refresh triggered from create/edit modals.

## Goals / Non-Goals

**Goals:**

- Let create/edit modals close promptly after the `Link` save succeeds.
- Start the initial RSS refresh in the background when the saved link requires one.
- Keep saved-link query invalidation independent from remote feed fetch completion.
- Refresh RSS subscription/status/feed-item caches again after the background fetch settles.
- Avoid duplicate HTTP failure toasts; generated API calls already use Halo's global error
  interceptor.

**Non-Goals:**

- Add a backend queue, job resource, or new API endpoint.
- Change scheduled RSS refresh or manual feed-page refresh behavior.
- Change feed parsing, caching, retention, or RSS status semantics.
- Guarantee that the initial background refresh completes before the user navigates away.

## Decisions

1. Use frontend fire-and-forget for modal-triggered initial refresh.

   The modal save success handler should close the modal and invalidate the saved-link queries
   immediately after create/patch succeeds. If initial RSS refresh is needed, it should start a
   `refreshLinkFeed` request without awaiting it in the save mutation's critical path.

   Alternative considered: add a backend async job endpoint. That would make completion more
   durable but adds lifecycle, observability, and cleanup concerns that are disproportionate for
   this UI latency issue.

2. Preserve the existing refresh API contract.

   `refreshLinkFeed` should continue to return `LinkFeedRefreshResult` and update `status.rss`.
   Manual refresh flows should still await it because their purpose is explicitly remote refresh
   followed by list reload.

   Alternative considered: make `refreshLinkFeed` itself return before fetch completion. That
   would break manual refresh expectations and require a new way to observe completion.

3. Centralize the modal initial-refresh trigger if duplication grows.

   Creation and editing currently contain similar initial-refresh logic. The implementation may
   extract a small frontend helper/composable to start background refresh and invalidate RSS
   queries after settlement, but only if that keeps the change smaller and clearer than duplicating
   promise handling in both modal files.

   Alternative considered: refactor all RSS refresh logic into a broader composable. That is out
   of scope for this latency fix.

## Risks / Trade-offs

- Background refresh can finish after the modal has closed, so completion feedback may arrive
  later or only be visible through updated RSS status. Mitigation: keep link save feedback immediate
  and refresh RSS-related queries when the background request settles.
- The user may leave the page before the browser request completes. Mitigation: scheduled refresh
  and manual refresh remain available; this change is a responsiveness improvement, not a durable
  job guarantee.
- Fire-and-forget code can hide failures if errors are swallowed incorrectly. Mitigation: keep the
  generated API client's global error handling active and only avoid duplicate local failure toasts.
