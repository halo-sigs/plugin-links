## 1. Backend Data Model And Storage

- [x] 1.1 Add `spec.rss` and `status.rss` models to `Link` with OpenAPI schema descriptions and disabled-by-default behavior
- [x] 1.2 Add Nitrite and RSS/Atom parser dependencies to Gradle, matching the plugin dependency style
- [x] 1.3 Create a shared Links embedded database component for `links.nitrite` with open, close, backup, recovery, commit, and compact behavior
- [x] 1.4 Define feed item and feed query domain models for cached RSS item persistence
- [x] 1.5 Implement `LinkFeedItemStore` with deterministic upsert, indexed cursor listing, count, and delete operations
- [x] 1.6 Implement retention cleanup by per-link count, global count, and item age limits
- [x] 1.7 Persist cached feed item read state outside Halo Extension storage

## 2. Feed Fetching And Security

- [x] 2.1 Refactor reusable URL validation/fetch helpers so link detail scraping and RSS feed requests share the SSRF boundary
- [x] 2.2 Implement RSS/Atom feed discovery from website HTML without automatically enabling RSS
- [x] 2.3 Implement feed fetching with timeout, maximum response size, redirect validation, ETag, and Last-Modified support
- [x] 2.4 Parse RSS/Atom entries into sanitized cached item records with truncated plain-text summaries
- [x] 2.5 Update `Link.status.rss` only when visible status changes or when a coarse successful refresh timestamp should be recorded
- [x] 2.6 Add unit tests for private URL blocking, unsafe redirects, response limits, duplicate item upsert, and status update behavior

## 3. Console APIs And Scheduling

- [x] 3.1 Add Console endpoints for feed discovery, manual link refresh, cursor-based feed item listing, and feed retention cleanup where needed
- [x] 3.2 Add RBAC rules for RSS feed read and manage operations under the existing link view/manage role templates
- [x] 3.3 Add scheduled refresh for enabled links with conservative batching and per-feed failure isolation
- [x] 3.4 Add scheduled retention cleanup and compact the embedded database after large delete batches
- [x] 3.5 Regenerate OpenAPI docs and the generated TypeScript API client with `./gradlew generateApiClient`
- [x] 3.6 Add read-state filtering and mark-read APIs for cached feed items

## 4. Console UI

- [x] 4.1 Add RSS settings to the link creation/editing form, including enable toggle, feed URL input, and feed discovery action
- [x] 4.2 Display per-link RSS status in the link list or link editing surface without crowding the existing management UI
- [x] 4.3 Add a Console route for recent friend-link updates with cursor loading, link filter, group filter, and external article links
- [x] 4.4 Add manual refresh actions with loading, success, and failure states
- [x] 4.5 Render feed item titles and summaries as sanitized or plain text content
- [x] 4.6 Automatically refresh RSS after first enabling RSS tracking for a link
- [x] 4.7 Add read/unread filtering and mark-read actions to the friend-link updates view
- [x] 4.8 Simplify navigation and refresh controls based on Console smoke-test feedback
- [x] 4.9 Limit RSS updates view link and group filter choices to subscribed links

## 5. Verification

- [x] 5.1 Run backend unit tests with `./gradlew test`
- [x] 5.2 Run UI type checking with `pnpm --dir console type-check`
- [x] 5.3 Run UI linting with `pnpm --dir console lint`
- [x] 5.4 Run `./gradlew build` to verify backend, frontend, generated resources, and packaging
- [x] 5.5 Validate the OpenSpec change with `openspec validate add-link-rss-feed --strict`
- [x] 5.6 Smoke test in Halo Console: enable RSS for a link, discover a feed, refresh manually, view recent items, filter by link/group, and confirm unsafe URLs are rejected
