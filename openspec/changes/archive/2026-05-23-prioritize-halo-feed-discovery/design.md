## Context

Feed discovery currently fetches the submitted website URL as HTML and extracts
RSS/Atom candidates from `<link rel="alternate">` elements. Halo sites commonly
publish post and moment feeds at stable root-relative paths, even when the page
being inspected does not expose those paths in HTML metadata.

The existing Console action already fills `spec.rss.feedUrls` from the
`feedUrls` discovery response, so this change can stay backend-only and keep the
API contract unchanged.

## Goals / Non-Goals

**Goals:**

- Prefer Halo default feed paths before the existing HTML alternate-link
  discovery.
- Return one or both valid Halo default feed URLs when they are available.
- Preserve the current HTML discovery fallback when Halo default paths are not
  valid feeds.
- Reuse the existing RSS request safety boundary for candidate feed requests.

**Non-Goals:**

- Add new discovery paths beyond `/rss.xml` and `/feed/moments/rss.xml`.
- Change the `rss/discovery` API response shape or generated client types.
- Automatically enable RSS outside the existing Console fill behavior.
- Add feed-content probing for arbitrary guessed paths.

## Decisions

### Derive Halo candidates from the website origin

Build candidate URLs from the submitted website URL's origin plus the two
root-relative paths:

- `/rss.xml`
- `/feed/moments/rss.xml`

For example, discovering `https://example.com/about` tries
`https://example.com/rss.xml` and `https://example.com/feed/moments/rss.xml`.

**Rationale**: The requested paths are Halo site-level conventions, not paths
relative to the current page.

**Alternative considered**: Resolve candidates relative to the submitted page
path. That would turn `https://example.com/about` into
`https://example.com/about/rss.xml`, which does not match Halo's convention.

### Validate candidates as feeds before returning them

Fetch each Halo candidate with the existing RSS feed fetch options and parse the
body with the same RSS/Atom parser family used by refresh. Treat only successful,
parseable feed responses as discovered URLs. Ignore missing, non-successful, or
non-feed responses for this default-path pass.

**Rationale**: A successful HTTP response alone can be an HTML error page or
custom route, and should not be written into RSS settings.

**Alternative considered**: Return candidates after any 2xx response. That is
faster but can fill invalid URLs and defer the failure to refresh time.

### Return Halo defaults before HTML discovery

Attempt both Halo candidates first. If the valid candidate list is non-empty,
return it immediately and do not fetch the website HTML for alternate-link
matching. If neither candidate is valid, run the existing HTML discovery logic
unchanged.

**Rationale**: The user intent is to give Halo sites a privileged discovery
path. Returning immediately also avoids mixing convention-based feeds with
possibly noisy HTML-discovered feeds for the same site.

**Alternative considered**: Merge Halo defaults and HTML-discovered feeds. This
could surface more feeds, but it changes the current one-click fill behavior into
a broader aggregation step and may surprise users with duplicates or unrelated
feeds.

### Keep the API and Console unchanged

Keep `LinkFeedDiscoveryResult.feedUrls` as the only response field. The Console
already enables RSS and merges returned URLs into the textarea when discovery
succeeds.

**Rationale**: The behavior change is purely in the discovery source order, not
in the data model or UI contract.

## Risks / Trade-offs

- [Risk] Default-path probing adds up to two network requests before HTML
  fallback. -> Mitigation: keep requests inside the existing bounded discovery
  workflow and use the existing feed fetch timeout/size limits.
- [Risk] A Halo default feed may redirect. -> Mitigation: rely on the existing
  safe redirect handling and return only URLs that remain inside the safety
  boundary and parse as feeds.
- [Risk] A candidate request can fail for reasons unrelated to feed absence. ->
  Mitigation: ignore individual candidate failures and fall back to HTML
  discovery when no valid default feed is found.
