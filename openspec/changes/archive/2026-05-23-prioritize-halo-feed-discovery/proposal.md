## Why

Halo sites expose stable RSS endpoints at predictable paths, but some pages do
not advertise those feeds through HTML `<link rel="alternate">` metadata. Feed
discovery should recognize those Halo conventions first so users can fill
subscription URLs with one click more often.

## What Changes

- Before the existing HTML alternate-link discovery runs, try the website
  origin plus `/rss.xml` and `/feed/moments/rss.xml`.
- When one or both Halo default feed URLs return a valid RSS/Atom feed, return
  those URLs as the discovery result and skip the current HTML matching path.
- Preserve the existing fallback behavior: if no Halo default feed is available,
  continue discovering feed URLs from the website HTML as today.
- Keep the existing discovery API response shape (`feedUrls`) and Console fill
  behavior.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `link-rss-feed`: Feed discovery now prioritizes Halo default RSS endpoints
  before falling back to HTML alternate-link discovery.

## Impact

- **Backend**: Updates RSS feed discovery in `DefaultLinkFeedService` and
  `LinkFeedFetcher` so candidate Halo feed URLs can be fetched and validated
  before HTML discovery fallback.
- **Frontend**: No API shape change; the existing Console discovery action keeps
  filling `spec.rss.feedUrls` from the returned list.
- **Security**: Halo default feed candidate requests must use the same SSRF,
  redirect, timeout, and response-size protections as existing RSS feed
  requests.
- **Tests**: Add backend coverage for successful Halo default discovery,
  multiple Halo defaults, and fallback to HTML discovery when defaults are not
  valid feeds.
