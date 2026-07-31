## 1. Lock the transport contracts with tests

- [x] 1.1 Add CAPTCHA service tests for transport-neutral issuance, explicit identifier
  verification, Cookie adapter behavior, five-minute expiry, single-use replay protection, and
  independent REST challenges.
- [x] 1.2 Add REST endpoint tests for the exact CAPTCHA and application paths, anonymous access,
  disabled behavior, JSON-only media handling, malformed JSON, unknown properties, field
  normalization, and `feedUrls` arrays.
- [x] 1.3 Add REST workflow tests proving settings, CAPTCHA consumption, shared Form/REST generation
  and submission limits, `FORM` origin, validation, duplicate rules, capacity, persistence, and
  notification-failure semantics are reused in the required order.
- [x] 1.4 Add REST response tests for the `201 {id,status}` result and every Halo Problem Details
  status, type, safe detail, `errors`, and `retryAfterSeconds` contract.
- [x] 1.5 Replace Form response-negotiation tests with regressions proving every supported Form
  request returns its redirect contract regardless of `Accept`, unsupported media returns `415`,
  and no `406`, JSON envelope, or `Vary: Accept` remains.

## 2. Refactor the shared CAPTCHA boundary

- [x] 2.1 Refactor CAPTCHA issuance to return the opaque challenge identifier, PNG bytes, and
  lifetime independently from any HTTP response or Cookie.
- [x] 2.2 Refactor verification to accept an explicit challenge identifier and answer while
  preserving atomic consumption, normalization, expiry, capacity cleanup, and replay behavior.
- [x] 2.3 Adapt the native Form route to the refactored core while preserving its existing Cookie
  attributes, prior-Cookie invalidation, Cookie expiry, PNG cache headers, generation errors, and
  security ordering.
- [x] 2.4 Ensure native Form and REST adapters use the same CAPTCHA generation limiter, store,
  rendering gate, lifecycle cleanup, and sensitive-data logging boundary.

## 3. Implement the public REST application resource

- [x] 3.1 Add annotated request, CAPTCHA response, and created-result DTOs with the agreed required
  fields, optional fields, list shape, normalization, unknown-property, and OpenAPI contracts.
- [x] 3.2 Add the public `api.link.halo.run/v1alpha1` CustomEndpoint operations for REST CAPTCHA
  issuance and JSON application creation without adding plugin-owned CORS or Cookie/CSRF behavior.
- [x] 3.3 Map decoded REST input to one `FORM`-origin `LinkApplicationService.Submission`, applying
  the shared visitor switch, explicit CAPTCHA, shared submission limiter, and creation service in
  the specified order.
- [x] 3.4 Return `201 Created` with only the created metadata name as `id` and `status=PENDING`, with
  no submitted fields, resource representation, anonymous read route, or `Location` header.
- [x] 3.5 Map expected failures to Halo-rendered status exceptions with the specified stable Problem
  type URIs, Halo-style validation `errors`, safe Chinese details, and body-only
  `retryAfterSeconds`.
- [x] 3.6 Preserve fail-closed behavior and safe diagnostics for settings, CAPTCHA, capacity, and
  persistence failures without logging challenge secrets, submitted fields, or raw IPs.
- [x] 3.7 Add the minimum anonymous `create` RBAC grants for the REST application and CAPTCHA
  resources and verify no read or management permission is introduced.

## 4. Narrow the native Form transport

- [x] 4.1 Remove `Accept` selection, negotiated JSON response builders and envelopes, JSON outcome
  codes, `406` handling, and `Vary: Accept` from `/links/apply/submit`.
- [x] 4.2 Keep `application/x-www-form-urlencoded` as the only supported request media, preserve
  `415` for other media, and retain every existing business-result redirect and CSRF boundary.
- [x] 4.3 Remove code and test helpers made unused by deleting Form JSON negotiation without
  refactoring unrelated route behavior.

## 5. Publish OpenAPI and integration documentation

- [x] 5.1 Regenerate the plugin OpenAPI document and TypeScript client, then verify both public REST
  operations and their DTOs are present in the existing Links API group with no unrelated generated
  drift.
- [x] 5.2 Rewrite `dev/theme-api.md` to separate native Form and REST contracts, remove the beta
  asynchronous Form example and envelope table, and document the migration to the REST resource.
- [x] 5.3 Add complete cURL and browser `fetch` examples covering explicit CAPTCHA acquisition,
  `credentials: "omit"`, JSON submission, `201`, Problem `status + type`, challenge refresh, and
  generic failure fallback.
- [x] 5.4 Document Halo-owned CORS, create-only anonymous scope, absent idempotency and status lookup,
  process-local security state, and the lack of a platform-specific mini-program SDK.

## 6. Verify the complete change

- [x] 6.1 Run targeted CAPTCHA, Form router, REST endpoint, application service, limiter, capacity,
  concurrency, and RBAC tests and fix every contract failure.
- [x] 6.2 Start the local Halo development server and smoke-test native Form redirects plus
  anonymous REST CAPTCHA, successful creation, invalid/replayed CAPTCHA, duplicate, rate limit,
  disabled behavior, CORS preflight, and cleanup of test applications.
- [x] 6.3 Run `./gradlew test`, the generated-client frontend type check using the repository-pinned
  package manager, and `./gradlew build`.
- [x] 6.4 Run `openspec validate add-link-application-rest-api --strict`, generated and source diff
  checks, and `git diff --check`.
