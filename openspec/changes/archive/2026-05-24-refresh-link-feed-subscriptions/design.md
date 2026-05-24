## Context

The existing Console feed page loads cached feed items through `useLinkFeedItems().load()`. The page action labeled "刷新" calls only that local cache reload and does not invoke the existing remote refresh endpoint, `POST links/{name}/rss/refresh`.

The page already has the data needed to refresh subscriptions: `useRssLinksFetch()` returns enabled RSS links with non-empty `spec.rss.feedUrls`, and the generated client already exposes single-link refresh through the link feed API. The current gap is mostly orchestration and UI semantics in the Console.

## Goals / Non-Goals

**Goals:**

- Let users refresh the currently selected RSS subscription from the friend-link feed page.
- Let users refresh all enabled RSS subscriptions from the friend-link feed page.
- Reload the cached item list after remote refresh so newly fetched items appear in the active filter.
- Rename cache-only reload actions so they no longer imply upstream RSS fetching.
- Preserve partial success: one failed subscription must not prevent other subscriptions from refreshing.

**Non-Goals:**

- Do not change the `Link.spec.rss.feedUrls` data model.
- Do not add a new feed-level subscription entity.
- Do not change scheduled refresh behavior.
- Do not add per-user read/favorite/read-later state; this remains site-level for now.

## Decisions

1. Reuse the existing single-link refresh endpoint for Console refresh orchestration.

   The Console will call `refreshLinkFeed({ name })` for the selected link or for each link returned by `useRssLinksFetch()`. A new backend batch endpoint is not required for the first implementation because the backend already owns validation, SSRF protection, multi-feed refresh, item caching, and status updates per link.

   Alternative considered: add `POST rss/-/refresh` for batch refresh. This would centralize concurrency and response aggregation, but it adds new API surface and generated-client churn before the UI behavior actually needs it.

2. Put refresh orchestration in a small frontend composable.

   A composable such as `useLinkFeedRefresh()` should accept `Link` objects, call the generated API, expose loading state, aggregate success/failure counts, and keep toast/error behavior out of `LinkFeedList.vue`.

   Alternative considered: implement all refresh loops directly in `LinkFeedList.vue`. That is faster initially but would make the page component own API orchestration, progress state, and messaging.

3. Separate remote refresh from cache reload in UI text.

   The main feed controls should use explicit labels such as "刷新当前" and "刷新全部" for remote feed refresh. Cache-only list reload actions should use "重新加载" so users can tell whether the action fetches upstream RSS feeds or only re-queries local cached items.

   Alternative considered: keep one button and change its behavior to refresh remote feeds. This hides the useful cache-only reload operation and is ambiguous when no subscription is selected.

4. Preserve active feed filters after refresh.

   After any remote refresh completes, call the existing `load()` for the active feed item list without appending so pagination resets under the current selected link/read filter. Favorite and read-later modal cache reload buttons can remain cache-only and should be renamed if their behavior does not change.

## Risks / Trade-offs

- Many subscriptions could produce many Console API requests -> keep the first implementation sequential or use a small concurrency limit, and show aggregate completion feedback instead of one toast per link.
- A selected subscription can disappear while refresh is running -> resolve the selected `Link` from the current `allLinks` map at click time and show a simple failure/disabled state if it is missing.
- Partial failures may be noisy -> aggregate failures into one message and rely on existing per-link `status.rss.lastError`/feed status for detail.
- Frontend-only orchestration means refresh-all depends on the current Console link list -> acceptable for the feed page workflow because `useRssLinksFetch()` is already the page's subscription source.
