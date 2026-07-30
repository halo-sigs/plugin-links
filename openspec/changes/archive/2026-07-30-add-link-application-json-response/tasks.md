## 1. Lock the Public Response Contract with Tests

- [x] 1.1 Extend `LinkRouterTest` with reusable JSON request and envelope assertions, then cover
  `201 APPLICATION_CREATED` without `field`, submitted values, generic data, or application
  resource details.
- [x] 1.2 Add negotiation tests for explicit JSON preference, quality weights, missing `Accept`,
  `*/*`, HTML preference, JSON/HTML ties, and unsupported response types.
- [x] 1.3 Add a JSON outcome matrix for disabled submission, invalid CAPTCHA, every field-validation
  path, duplicate submission, rate limiting, pending capacity, operational failure, and successful
  persistence with notification failure.
- [x] 1.4 Add protocol-boundary assertions for `Vary: Accept`, JSON `Cache-Control: no-store`,
  unsupported request media, native Halo CSRF rejection, CAPTCHA cookie expiry, and non-reflection
  of attacker-controlled values.
- [x] 1.5 Preserve and extend exact legacy regression assertions for every existing success and
  error `303 Location`, including absence, wildcard, HTML-preferred, and tied `Accept` requests.

## 2. Expose Accurate Submission Retry Timing

- [x] 2.1 Add focused `LinkApplicationRateLimiterTest` cases for allowed admission, rejected
  admission with ceiling-rounded positive remaining seconds, exact boundary admission, concurrent
  same-IP checks, and existing bounded cleanup.
- [x] 2.2 Replace the limiter's boolean result with the minimal admission result required by the
  tests while preserving atomic per-IP updates, the one-minute window, and bounded storage.

## 3. Implement Negotiated Visitor Responses

- [x] 3.1 Add the minimal JSON envelope and response-selection logic for the stable `status`, `code`,
  optional `field`, and display `message` contract.
- [x] 3.2 Refactor `/links/apply` so one settings, CAPTCHA, rate-limit, validation, duplicate,
  capacity, persistence, and notification flow maps to either the exact legacy redirect or the
  specified JSON status and code.
- [x] 3.3 Handle the exact POST path with unsupported request media as `415`, return the JSON
  `UNSUPPORTED_MEDIA_TYPE` envelope only when JSON is negotiated, and return an empty standard
  `406` when no supported response representation is acceptable.
- [x] 3.4 Add `Vary: Accept` to negotiated HTML and JSON results, add `Cache-Control: no-store` to
  JSON, and emit the limiter's accurate `Retry-After` only on JSON `429` responses.
- [x] 3.5 Verify response negotiation does not change CAPTCHA consumption, formal submission
  allowance, duplicate-before-capacity ordering, fail-closed settings behavior, operational
  diagnostics, or notification-after-persistence success semantics.

## 4. Document Theme Integration

- [x] 4.1 Update `dev/theme-api.md` with the content-negotiation rules, JSON envelope, stable and
  additive code table, exact HTTP status mapping, headers, and platform-error boundary.
- [x] 4.2 Replace the redirect-only JavaScript snippet with a complete same-origin example using
  `URLSearchParams`, explicit JSON acceptance, pending-button state, content-type checking,
  text-safe messages, field focus, unknown-code and non-JSON fallback, and CAPTCHA refresh.
- [x] 4.3 Keep CSRF token and `linkApplicationEnabled` delivery strategy theme-defined, and document
  the unchanged no-JavaScript form and redirect contract alongside the JSON opt-in.

## 5. Validate the Change

- [x] 5.1 Run focused route and rate-limiter tests and confirm every new contract assertion passes.
- [x] 5.2 Run the full backend test suite and `./gradlew build`.
- [x] 5.3 Run `openspec validate add-link-application-json-response --strict` and
  `git diff --check`, then review the final diff for only the approved backend, test, documentation,
  and OpenSpec scope.
