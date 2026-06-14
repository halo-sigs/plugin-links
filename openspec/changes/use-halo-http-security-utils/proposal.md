## Why

Halo 2.25 exposes `HttpSecurityUtils` from the API module so plugins can reuse the
same SSRF-safe HTTP client and response-size filter used by Halo itself. The
links plugin currently maintains its own SSRF-safe fetcher stack, which duplicates
platform logic and increases long-term maintenance risk.

## What Changes

- Upgrade the Halo plugin platform/API dependency and local dev runtime to
  Halo 2.25.0.
- **BREAKING**: raise the plugin's minimum supported Halo version to `>=2.25.0`
  because the implementation will import API classes first exposed to plugins in
  Halo 2.25.
- Refactor external URL fetching for link details, RSS discovery/fetching, and
  link verification to use `run.halo.app.infra.utils.HttpSecurityUtils`:
  - `secureHttpClient()` for SSRF-safe outbound connections.
  - `maxResponseSizeFilter(long)` for response body bounds.
- Preserve the plugin-facing fetch behavior needed by existing services:
  status code, final URL, response body, parsed HTML document when requested,
  `ETag`, and `Last-Modified`.
- Remove the plugin-local SSRF utility and manual socket/redirect code after the
  Halo utility-backed fetcher covers the same safety boundaries.

## Capabilities

### New Capabilities

- *(none)*

### Modified Capabilities

- `ssrf-protection`: external HTTP requests continue to be protected from
  private, local, and special-use network targets, but the security enforcement
  is delegated to Halo 2.25's API-provided HTTP security utilities instead of
  plugin-maintained URL/IP validation and pinned socket code.

## Impact

- **Backend**: `build.gradle`, `plugin.yaml`, `run.halo.links.security`, and the
  tests around link detail, RSS fetching, and verification.
- **Frontend**: no Console UI changes expected.
- **API**: no endpoint contract or generated TypeScript client changes expected.
- **Dependencies**: Halo platform/API moves from 2.24.0 to 2.25.0; no new
  third-party dependency should be needed because WebFlux/Reactor Netty are
  already available through Halo.
