## 1. CAPTCHA Resources and Test Seams

- [x] 1.1 Add a clearly licensed bundled font and its license notice, then verify deterministic
  headless loading without depending on host fonts.
- [x] 1.2 Add focused test fixtures for a controllable clock, random source, remote address, cookie
  jar, and concurrent challenge operations.

## 2. Challenge Generation and State

- [x] 2.1 Write generator tests for the fixed five-character unambiguous alphabet, trimming,
  exact-five-ASCII-character validation, case-insensitive comparison, `160 x 48` PNG output, and
  packaged-font rendering.
- [x] 2.2 Implement the SecureRandom-backed Java2D generator and off-event-loop PNG encoding needed
  to pass the generator tests.
- [x] 2.3 Write challenge-store tests for five-minute expiry boundaries, 10,000-entry capacity,
  lazy cleanup, no capacity-driven live-entry eviction, refresh invalidation of the previous
  cookie challenge, atomic consume-on-every-attempt, replay rejection, and concurrent single-winner
  verification.
- [x] 2.4 Implement the process-local challenge store so it retains only normalized answers and
  expiry instants, clears on plugin shutdown, and passes the lifecycle and concurrency tests.

## 3. Cookie and Generation Abuse Controls

- [x] 3.1 Write cookie tests for opaque identifiers, path `/links`, five-minute max age, HttpOnly,
  SameSite=Lax, conditional Secure, overwrite, and expiration after every verification attempt.
- [x] 3.2 Implement the CAPTCHA cookie resolver without binding challenges to IP, authentication,
  session, or User-Agent.
- [x] 3.3 Write generation-limiter tests for 10 images per remote IP per minute, `Retry-After`,
  10,000 tracked IPs, bounded cleanup, and no plugin-side forwarding-header trust.
- [x] 3.4 Implement a dedicated CAPTCHA generation limiter that is independent from the existing
  application submission limiter.
- [x] 3.5 Write concurrency-gate tests proving that at most four drawings run at once and excess
  requests fail immediately without entering an unbounded queue.
- [x] 3.6 Implement the four-slot drawing gate and map limiter exhaustion to 429 and drawing,
  capacity, font, encoding, or store failures to fail-closed 503 responses.

## 4. Theme Route Integration

- [x] 4.1 Extend router tests first for `GET /links/captcha`: enabled PNG issuance, no-store headers,
  secure cookie behavior, disabled 404, generation 429, and fail-closed 503.
- [x] 4.2 Add `GET /links/captcha` to the theme router and connect settings gating, generation
  limiting, drawing, state issuance, cookie writing, and aggregate-only operational diagnostics.
- [x] 4.3 Extend form-route tests first for missing, malformed, incorrect, expired, correct, and
  replayed CAPTCHA submissions; generic 303 errors; no answer or application-field reflection; and
  no failed-draft persistence.
- [x] 4.4 Integrate CAPTCHA parsing and atomic verification after the disabled gate and form parsing
  but before the existing submission limiter and `LinkApplicationService.create`.
- [x] 4.5 Add ordering and isolation tests proving CAPTCHA failures do not consume the application
  allowance or call the shared service, successful verification does consume the challenge before
  business validation, and Comment/Console/internal creation paths remain unchanged.
- [x] 4.6 Add a real Security WebFilter integration test proving anonymous image access works,
  invalid CSRF still blocks a correct CAPTCHA, and valid CSRF plus CAPTCHA reaches application
  creation.

## 5. Initial Theme Contract Documentation

- [x] 5.1 Update `dev/theme-api.md` with the mandatory image, `captchaCode`, Cookie behavior,
  no-JavaScript HTML example, optional keyboard-operable refresh, JavaScript example, generic error,
  and image-only accessibility limitation.
- [x] 5.2 Document that the CAPTCHA-protected form is the initial published visitor-application
  contract because the feature has not been released.
- [x] 5.3 Verify that no CAPTCHA setting, LinkApplication field, CustomEndpoint, anonymous RBAC
  rule, generated OpenAPI client change, external provider abstraction, compatibility shim, legacy
  branch, migration, alias, or fallback was introduced.

## 6. Validation

- [x] 6.1 Run the focused CAPTCHA, route, rate-limit, lifecycle, concurrency, cookie, privacy, and
  CSRF test suites and record their passing counts.
- [x] 6.2 Run `./gradlew test` and `./gradlew build`, then resolve only failures caused by this
  change.
- [x] 6.3 Run strict OpenSpec validation for `add-link-application-image-captcha` and
  `git diff --check`, and confirm every implementation diff traces to this change's scope.
