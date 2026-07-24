## 1. Upgrade Halo runtime contract

- [x] 1.1 Update `build.gradle` to use `run.halo.tools.platform:plugin:2.25.0`.
- [x] 1.2 Update the `halo { version = ... }` local dev runtime to Halo 2.25.
- [x] 1.3 Update `src/main/resources/plugin.yaml` to require Halo `>=2.25.0`.
- [x] 1.4 Compile enough of the backend to confirm `run.halo.app.infra.utils.HttpSecurityUtils` is available from the plugin API dependency.

## 2. Refactor the shared external URL fetcher

- [x] 2.1 Refactor `SafeUrlFetcher` to build its HTTP client from `HttpSecurityUtils.secureHttpClient()`.
- [x] 2.2 Apply `HttpSecurityUtils.maxResponseSizeFilter(options.maxBodySize)` whenever response bodies are consumed.
- [x] 2.3 Preserve the existing `FetchOptions` and `FetchResult` contract, including status code, final URL, body, parsed document, `ETag`, and `Last-Modified`.
- [x] 2.4 Preserve current request headers, including `User-Agent`, `Accept`, `Referer`, `If-None-Match`, and `If-Modified-Since`.
- [x] 2.5 Preserve per-operation timeout behavior, including the shorter verification timeout.
- [x] 2.6 Keep explicit redirect handling with relative `Location` resolution, a 3-hop limit, and secure-client execution for every hop.
- [x] 2.7 Normalize invalid URL, blocked URL, redirect, response-size, and client failures to the existing `ServerErrorException` boundary.
- [x] 2.8 Preserve `allowOversizedBody` behavior for reachability checks by returning the response status with an empty body when the body exceeds the configured limit.

## 3. Remove duplicate plugin-local security code

- [x] 3.1 Remove `LinkSecurityUtils` after `SafeUrlFetcher` no longer depends on plugin-local address validation.
- [x] 3.2 Remove `RedirectHandler` after redirect handling is consolidated in the Halo utility-backed fetcher.
- [x] 3.3 Search the backend for remaining external fetch paths and verify link detail, RSS, and verification all go through the shared fetcher.
- [x] 3.4 Confirm no OpenAPI client regeneration is required because endpoint shapes, extension models, and DTO contracts did not change.

## 4. Update tests

- [x] 4.1 Replace tests that mock `LinkSecurityUtils`, `RedirectHandler`, or `Jsoup.connect` internals with tests against the `SafeUrlFetcher` behavior boundary.
- [x] 4.2 Cover direct private/local target blocking for link detail, RSS, and verification fetches.
- [x] 4.3 Cover non-HTTP(S) URL rejection and bad URL error normalization.
- [x] 4.4 Cover redirect-to-private target rejection and redirect limit enforcement.
- [x] 4.5 Cover response-size enforcement and the reachability `allowOversizedBody` status-preservation path.
- [x] 4.6 Cover RSS response metadata preservation, including `ETag`, `Last-Modified`, and `304 Not Modified`.

## 5. Verify

- [x] 5.1 Run `./gradlew test`.
- [x] 5.2 Run `./gradlew build`.
- [x] 5.3 Run `openspec validate use-halo-http-security-utils --strict`.
