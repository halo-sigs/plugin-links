## Why

Friend links are currently static directory entries. Adding RSS/Atom awareness lets site owners see recent posts from linked sites inside the Console, turning the links page into a lightweight relationship feed without overloading Halo's Extension storage with high-volume article data.

## What Changes

- Add optional RSS settings to each `Link` so users can enable feed tracking and provide or discover a feed URL.
- Store lightweight RSS runtime state on `Link.status.rss`, including the effective feed URL, last fetch timestamps, failure state, conditional request metadata, and cached item count.
- Add an embedded database-backed RSS item cache for high-volume feed items instead of storing article lists in Extension objects.
- Add backend services and Console APIs for feed discovery, scheduled refresh, manual refresh, cursor-based item listing, and retention cleanup.
- Add a Console "friend link updates" view that shows recent articles across enabled links with filtering by link/group.
- Extend the existing SSRF protection contract so feed discovery and feed fetching use the same external URL safety boundary as link detail scraping.

## Capabilities

### New Capabilities

- `link-rss-feed`: Configure RSS/Atom tracking for links, fetch and cache feed items in an embedded store, and expose recent friend-link articles in the Console.

### Modified Capabilities

- `ssrf-protection`: Apply URL validation and redirect safety requirements to RSS feed discovery and feed fetching.

## Impact

- **Backend**: Extends `Link` with `spec.rss` and `status.rss`, adds feed fetch/discovery services, embedded database infrastructure, retention cleanup, scheduled/manual refresh logic, and Console endpoints.
- **Frontend**: Adds RSS fields to link forms and a new Console view for recent friend-link articles, feed status, filtering, and manual refresh.
- **Storage**: Keeps RSS configuration and lightweight status in Extension storage; stores large feed item collections in a plugin-local embedded database file such as `links-feed.nitrite`.
- **Dependencies**: Adds embedded database dependencies using the established Nitrite-based local persistence pattern, plus an RSS/Atom parser dependency.
- **Security**: All server-side feed requests must reject unsafe URLs, private/reserved addresses, and unsafe redirects before connecting.
