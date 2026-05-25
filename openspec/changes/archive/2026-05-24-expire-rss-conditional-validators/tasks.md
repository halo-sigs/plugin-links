## 1. Status Model

- [x] 1.1 Add nullable validator freshness metadata to each per-feed RSS status.
- [x] 1.2 Ensure successful `200 OK` refreshes update validator freshness only when the response provides `ETag` or `Last-Modified`.
- [x] 1.3 Ensure `304 Not Modified` and failed refreshes preserve previous validators and validator freshness metadata.

## 2. Conditional Request Logic

- [x] 2.1 Add an internal validator freshness window constant of eight days.
- [x] 2.2 Send conditional request headers only when the feed URL has cached items and fresh validator metadata.
- [x] 2.3 Treat missing validator freshness metadata as stale and force a full fetch.
- [x] 2.4 Treat validator freshness older than the freshness window as stale and force a full fetch.
- [x] 2.5 Suppress `If-Modified-Since` when the stored `Last-Modified` value contains an invalid 2038 timestamp.

## 3. Tests

- [x] 3.1 Cover fresh validators being sent for a feed URL with cached items.
- [x] 3.2 Cover empty local cache skipping validators.
- [x] 3.3 Cover missing validator freshness metadata skipping validators.
- [x] 3.4 Cover stale validator freshness metadata skipping validators.
- [x] 3.5 Cover `200 OK`, `304 Not Modified`, and failed refresh status updates preserving or refreshing validator freshness correctly.
- [x] 3.6 Cover invalid 2038 `Last-Modified` values being omitted from conditional request headers.

## 4. Verification

- [x] 4.1 Regenerate the TypeScript API client because the Link status model changes.
- [x] 4.2 Run focused RSS service tests.
- [x] 4.3 Run the backend test suite.
- [x] 4.4 Run OpenSpec validation for `expire-rss-conditional-validators` and `link-rss-feed`.
- [x] 4.5 Run `git diff --check`.
