## 1. Backend Discovery

- [x] 1.1 Add a helper that derives `https://host/rss.xml` and `https://host/feed/moments/rss.xml` from the submitted website URL origin.
- [x] 1.2 Add candidate-feed probing that fetches each Halo default URL with the existing RSS feed fetch path and keeps only successful, parseable RSS/Atom responses.
- [x] 1.3 Update `DefaultLinkFeedService.discoverBlocking` to return valid Halo default feed URLs immediately and fall back to the current HTML alternate-link discovery only when none are valid.
- [x] 1.4 Preserve distinct, trimmed feed URL output and keep the existing `LinkFeedDiscoveryResult.feedUrls` response shape.

## 2. Tests

- [x] 2.1 Add a service test proving a valid `/rss.xml` default feed is returned before HTML discovery and `fetchHtml` is not called.
- [x] 2.2 Add a service test proving both `/rss.xml` and `/feed/moments/rss.xml` are returned when both candidates are valid feeds.
- [x] 2.3 Add a service test proving non-successful or non-feed Halo candidates are ignored and the existing HTML `<link rel="alternate">` discovery still runs.
- [x] 2.4 Add coverage for candidate URL derivation from a website URL with a non-root path.

## 3. Verification

- [x] 3.1 Run `./gradlew test --tests run.halo.links.rss.DefaultLinkFeedServiceTest`.
- [x] 3.2 Run `./gradlew test`.
- [x] 3.3 Run `openspec validate prioritize-halo-feed-discovery --strict`.
- [x] 3.4 Run `git diff --check`.
