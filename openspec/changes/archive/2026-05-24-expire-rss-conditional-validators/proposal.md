## Why

Some feed servers incorrectly keep returning `304 Not Modified` when stale
`ETag` or `Last-Modified` validators are sent. The RSS reader already skips
conditional headers when the local cache is empty, but it still needs a
periodic full-fetch path so cached feeds cannot be pinned forever by bad
validators.

## What Changes

- Track when each feed URL's conditional request validators were last refreshed
  from a real `200 OK` response.
- Skip `If-None-Match` and `If-Modified-Since` when the local item cache is
  empty, when validator age is unknown, or when validators are older than the
  configured freshness window.
- Preserve validator age on `304 Not Modified` and refresh it only after a real
  feed body is fetched successfully.
- Avoid sending clearly bad `Last-Modified` values such as 2038 timestamps.
- Keep the change limited to RSS/Atom refresh behavior; JSON Feed support is out
  of scope.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `link-rss-feed`: Clarify when RSS refresh may send conditional request
  validators and when it must force a full feed fetch.

## Impact

- Backend only.
- Updates `Link.status.rss.feeds[]` runtime status with validator freshness
  metadata.
- Updates RSS refresh logic in `DefaultLinkFeedService`.
- Adds regression tests for fresh, stale, missing, and malformed validator
  states.
- No frontend UI changes and no new external dependencies.
