## 1. Backend Model And Contract

- [x] 1.1 Replace `Link.RssSpec.feedUrl` with `feedUrls: List<String>` and remove single-feed config accessors/usages.
- [x] 1.2 Replace feed discovery output from `feedUrl` to `feedUrls` and update endpoint/OpenAPI descriptions.
- [x] 1.3 Redesign `Link.RssStatus` to keep Link-level aggregate fields plus per-feed status entries for URL, validators, failures, latest published time, and item count.
- [x] 1.4 Update refresh result DTOs to report aggregate Link refresh output and per-feed outcomes without exposing deprecated single-feed fields.
- [x] 1.5 Add backend validation/normalization for RSS config: trim blanks, de-duplicate URLs, require at least one HTTP/HTTPS URL when RSS is enabled, and reject unsafe URLs before fetch.

## 2. Refresh And Storage Behavior

- [x] 2.1 Update feed discovery to collect all valid RSS/Atom alternate links from a website response.
- [x] 2.2 Update manual refresh to iterate every configured feed URL for a Link with per-feed failure isolation.
- [x] 2.3 Update scheduled refresh to process only RSS-enabled links with non-empty `feedUrls` and to attempt every configured feed URL.
- [x] 2.4 Move ETag/Last-Modified lookup and empty-cache conditional-header checks to the per-feed status/cache scope.
- [x] 2.5 Update cached item ID generation so entries are scoped by link name, source feed URL, and entry identity.
- [x] 2.6 Extend `LinkFeedItemStore` where needed for per-feed counts while preserving existing Link-level listing and retention behavior.
- [x] 2.7 Update status write logic so removed feed URLs do not retain stale per-feed status entries.

## 3. Console UI And Client Usage

- [x] 3.1 Regenerate OpenAPI docs and the Console TypeScript client after backend contract changes.
- [x] 3.2 Update `LinkForm` state to use `rss.feedUrls: string[]` and render RSS URLs with a FormKit `code` input where each line is one URL.
- [x] 3.3 Update link create/edit payloads to write `feedUrls` only and remove all `spec.rss.feedUrl` branches.
- [x] 3.4 Update feed discovery UI to append or populate discovered `feedUrls` with duplicate suppression.
- [x] 3.5 Update automatic refresh checks after create/edit to use the enabled flag plus non-empty `feedUrls`.
- [x] 3.6 Update subscribed-link filtering and RSS sidebar text to treat a Link with multiple feed URLs as one subscription.
- [x] 3.7 Update RSS status badges/tooltips to handle aggregate success, failure, and partial failure states.

## 4. Tests

- [x] 4.1 Update backend unit tests for RSS config validation and disabled-by-default behavior with `feedUrls`.
- [x] 4.2 Add refresh service tests for multiple feed URLs, per-feed validators, empty per-feed cache, partial failure, and aggregate status.
- [x] 4.3 Add item store tests for source-scoped item identity and per-feed counting.
- [x] 4.4 Update endpoint tests for discovery returning multiple URLs and refresh rejecting disabled or empty-feed-url links.
- [x] 4.5 Update Console type expectations and any component/composable tests affected by generated `feedUrls` models.

## 5. Verification

- [x] 5.1 Run `./gradlew test`.
- [x] 5.2 Run `./gradlew build`.
- [x] 5.3 Run `pnpm --dir console type-check`.
- [x] 5.4 Run `pnpm --dir console lint`.
- [x] 5.5 Run `openspec validate replace-rss-feed-url-with-feed-urls --strict`.
- [x] 5.6 Run `git diff --check`.
- [x] 5.7 Search for deprecated single-config references such as `spec.rss.feedUrl`, `rssFeedUrl`, and discovery-result `feedUrl`; keep only legitimate item source `feedUrl` fields.
- [x] 5.8 Smoke test in Halo Console: create or edit a Link with two feed URLs, refresh it, confirm items appear under one subscription, and confirm one failing feed URL does not block the other.
