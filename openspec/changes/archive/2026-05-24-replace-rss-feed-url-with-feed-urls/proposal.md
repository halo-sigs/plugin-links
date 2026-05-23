## Why

Some websites expose more than one useful RSS/Atom source, such as posts, notes,
comments, or category-specific feeds. The current link RSS model only accepts one
`spec.rss.feedUrl`, which forces users to choose one source even though the
content still belongs under the same friend link.

This RSS feature has not been released yet, so the model can be cleaned up as a
breaking change instead of carrying compatibility fields.

## What Changes

- **BREAKING**: Replace `spec.rss.feedUrl` with `spec.rss.feedUrls`, a list of
  absolute HTTP/HTTPS RSS or Atom URLs.
- Keep `spec.rss.enabled` as a single Link-level switch; individual feed URLs do
  not get their own enabled flag or separate subscription identity.
- **BREAKING**: Replace feed discovery results from a single `feedUrl` to
  `feedUrls`, allowing the Console to append one or more discovered sources to
  the Link-level RSS URL list.
- Refresh each configured feed URL for an enabled Link and aggregate cached
  items under that Link.
- Store per-feed refresh state internally under `status.rss` so conditional
  request metadata, failures, and item counts do not leak between feed URLs.
- Keep Console reading, filtering, and subscription sidebar behavior Link-based:
  a Link with multiple feed URLs still appears as one subscription.
- Update the link create/edit form to manage RSS URLs as newline-delimited text
  backed by `spec.rss.feedUrls`.
- Remove the single-feed API/client/UI assumptions rather than preserving
  deprecated `feedUrl` handling.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `link-rss-feed`: RSS configuration, refresh status, refresh behavior, cached
  item identity, and Console subscription behavior change from one feed URL per
  Link to multiple feed URLs per Link.

## Impact

- **Backend**: Updates `Link.RssSpec`, `Link.RssStatus`, refresh result models,
  refresh service logic, item identity generation, item-store counting, and
  tests to use `feedUrls`.
- **Frontend**: Updates generated API types, link create/edit form state,
  FormKit controls, feed discovery behavior, auto-refresh checks, and RSS
  subscription filtering to use `feedUrls`.
- **OpenAPI**: Requires regenerating backend OpenAPI docs and the Console
  TypeScript client after model changes.
- **Storage**: Existing unreleased `feedUrl` configuration/status data can be
  discarded or migrated opportunistically during development; no released data
  compatibility layer is required.
- **Security**: Each configured feed URL must pass the existing RSS URL safety
  checks before fetching.
