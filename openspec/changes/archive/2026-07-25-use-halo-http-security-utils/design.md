## Context

`SafeUrlFetcher` is the shared outbound fetch boundary for link detail scraping,
RSS/Atom discovery and fetching, and link verification. It currently combines
several concerns in plugin-owned code:

- URL scheme and resolved-address validation in `LinkSecurityUtils`.
- Manual redirect handling and redirect target validation.
- HTTP fetching through Jsoup for HTTP URLs.
- A custom HTTPS socket path that pins the connection to a validated address.
- Response body size checks, charset decoding, status/header capture, and
  optional Jsoup document parsing.

Halo 2.25 moved `run.halo.app.infra.utils.HttpSecurityUtils` into the API module,
so plugins can now use the same secure Reactor Netty client and response-size
filter as Halo itself. The utility exposes `secureHttpClient()` and
`maxResponseSizeFilter(long)`.

## Goals / Non-Goals

**Goals:**

- Upgrade the plugin build and local dev runtime to Halo 2.25.0.
- Raise the plugin runtime requirement to `>=2.25.0`.
- Replace plugin-maintained SSRF networking code with Halo API-provided HTTP
  security utilities.
- Preserve the current `SafeUrlFetcher.FetchOptions` and `FetchResult` contract
  for link detail, RSS, and verification callers.
- Preserve redirect validation, redirect limits, body-size limits, timeouts,
  status code handling, final URL reporting, `ETag`, `Last-Modified`, and HTML
  document parsing where callers rely on them.

**Non-Goals:**

- No Console UI changes.
- No public endpoint or generated TypeScript API client changes.
- No new outbound proxy, allowlist, or operator-configurable HTTP policy.
- No change to Halo's `HttpSecurityUtils` behavior inside this plugin change.

## Decisions

### 1. Require Halo 2.25.0 directly

The implementation should update the platform dependency, dev runtime, and
plugin manifest requirement together:

- `run.halo.tools.platform:plugin:2.25.0`
- `halo.version = '2.25'`
- `spec.requires: ">=2.25.0"`

**Rationale:** importing `HttpSecurityUtils` from the API module is only safe for
plugin runtime environments that provide Halo 2.25 or newer.

**Alternative considered:** keep `>=2.22.5` and use reflection or a fallback
fetcher. That would preserve compatibility but keep two security implementations
alive, which is the maintenance problem this change is meant to remove.

### 2. Keep `SafeUrlFetcher` as the plugin adapter

`LinkRequest`, `LinkFeedFetcher`, and `LinkVerificationFetcher` should keep using
`SafeUrlFetcher.fetch(...)`. The internals of `SafeUrlFetcher` should move to a
WebFlux `WebClient` backed by:

- `ReactorClientHttpConnector(HttpSecurityUtils.secureHttpClient())`
- `HttpSecurityUtils.maxResponseSizeFilter(options.maxBodySize)`

**Rationale:** callers already depend on the fetcher's small result model rather
than raw HTTP APIs. Keeping that adapter makes the refactor surgical and avoids
cross-service churn.

**Alternative considered:** inject `WebClient` into every caller and let each
service interpret responses. That spreads redirect, timeout, response-size, and
header handling across unrelated services.

### 3. Preserve per-operation timeout behavior

The secure client returned by Halo sets secure defaults, including disabled
automatic redirects. `SafeUrlFetcher` should derive the Reactor Netty client from
`HttpSecurityUtils.secureHttpClient()` and then apply the current operation's
timeout from `FetchOptions` so link verification can keep its shorter timeout.

**Rationale:** RSS fetching currently uses the default 10s timeout, while link
verification uses 5s. The security utility should replace the SSRF mechanism, not
silently change verification latency.

**Alternative considered:** accept Halo's default timeout for every operation.
That is simpler but changes verification behavior.

### 4. Keep manual redirect handling in the fetcher

`HttpSecurityUtils.secureHttpClient()` disables automatic redirects. The fetcher
should continue to inspect redirect responses, resolve relative `Location`
headers against the current URL, enforce the redirect limit, and re-run each hop
through the same secure WebClient.

**Rationale:** redirect targets are separate outbound connections. Sending every
hop through the secure client preserves the existing safety boundary without
maintaining plugin-local IP filtering code.

**Alternative considered:** enable automatic redirects in the Reactor Netty
client. That would make redirect behavior harder to reason about and bypass the
plugin's explicit hop limit.

### 5. Use response exchange APIs, not `retrieve()`

The fetcher should use an API that exposes status and headers before body
decoding, such as `exchangeToMono`, then build `FetchResult` from:

- final URL
- HTTP status code
- response body
- parsed Jsoup document when `FetchOptions.parseDocument` is true
- `ETag`
- `Last-Modified`

**Rationale:** callers use non-2xx status codes as data. For example, RSS refresh
handles `304 Not Modified`, and verification records HTTP status in link status.
`retrieve()` would turn many of these responses into exceptions.

**Alternative considered:** use `retrieve()` and map exceptions back to status
results. That is more fragile and obscures normal non-2xx outcomes.

### 6. Normalize fetch errors at the adapter boundary

`SafeUrlFetcher` should continue throwing `ServerErrorException` for invalid
URLs, blocked URLs, redirects, body-size violations, and IO/client failures. For
link detail requests, the endpoint should continue converting invalid or blocked
URL failures into HTTP 400.

When `FetchOptions.allowOversizedBody` is true, the fetcher should preserve the
status code and return an empty body if the response body exceeds the configured
limit. This preserves the current reachability-check behavior.

**Rationale:** the refactor changes the lower-level exception types from custom
validation/IO errors to WebClient/Reactor/DataBuffer errors. Callers should not
need to learn those details.

**Alternative considered:** let WebClient exceptions propagate directly. That
would make endpoint and status error messages less stable.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Halo's blocked address set is stricter than the plugin-local filter and may block additional special-use ranges | Accept the stricter platform policy because these are external friend-link requests, not intranet fetches |
| Response decoding may change if the refactor uses WebClient string decoding directly | Preserve the current charset-aware body decoding by reading bytes and decoding from `Content-Type` where practical |
| Redirect handling could accidentally lose `ETag` or `Last-Modified` from the final response | Add tests around RSS fetch results and `304 Not Modified` behavior |
| Tests that mocked Jsoup/static URL utilities will no longer cover the new path | Replace them with tests at the `SafeUrlFetcher` boundary and service-level tests that assert behavior rather than implementation details |

## Migration Plan

1. Upgrade Halo dependencies and plugin runtime metadata.
2. Refactor `SafeUrlFetcher` internals to use Halo's secure HTTP utilities while
   retaining the existing public nested types.
3. Delete plugin-local `LinkSecurityUtils` and `RedirectHandler` once no code or
   tests reference them.
4. Update tests to cover blocked private/local targets, redirect blocking,
   response size limits, RSS conditional headers, and verification status
   preservation.
5. Run `./gradlew test` and `./gradlew build`.

Rollback is a normal code rollback of this change. Because the manifest minimum
Halo version changes, the dependency and manifest updates should be reverted
together if the refactor is rolled back.

## Open Questions

- None.
