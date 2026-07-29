## MODIFIED Requirements

### Requirement: Link application settings govern new application creation
The system SHALL provide a disabled-by-default Link Application settings group whose master switch
controls all new LinkApplication creation channels and SHALL require the built-in image CAPTCHA
whenever visitor submission is effective.

#### Scenario: Application feature is disabled by default
- **WHEN** the plugin has no Link Application settings
- **THEN** the application master switch is treated as disabled
- **AND** neither visitor forms nor Comment recognition creates a LinkApplication

#### Scenario: Master switch disables visitor submissions
- **WHEN** the application master switch is disabled
- **AND** a visitor posts a valid form to `/links/apply`
- **THEN** the system does not create a LinkApplication
- **AND** redirects to `/links?applied=disabled&message=友链申请功能暂未开放`

#### Scenario: Master switch disables Comment recognition
- **WHEN** the application master switch is disabled
- **AND** a matching new Comment is delivered to the recognition processor
- **THEN** the processor skips the Comment without calling the model
- **AND** does not create a LinkApplication

#### Scenario: Existing applications remain actionable while disabled
- **WHEN** the application master switch is disabled
- **THEN** administrators can still list, inspect, approve, reject, resume, and delete existing
  applications according to their lifecycle state

#### Scenario: Visitor and Comment channels are independently configurable
- **WHEN** the master switch is enabled
- **THEN** visitor form creation is controlled by the visitor-submission child switch
- **AND** Comment recognition is controlled by its own child switch, model, and source settings

#### Scenario: Visitor submission automatically requires CAPTCHA
- **WHEN** the master switch and visitor-submission child switch are enabled
- **THEN** the image CAPTCHA endpoint is available
- **AND** every request through `/links/apply` requires a valid CAPTCHA
- **AND** no independent CAPTCHA setting or compatibility fallback is exposed

#### Scenario: CAPTCHA does not govern other creation channels
- **WHEN** Console, Comment recognition, or an internal service creates a LinkApplication
- **THEN** CAPTCHA verification is not required
- **AND** the channel keeps its existing settings and validation behavior

#### Scenario: Application settings cannot be loaded
- **WHEN** application settings are missing or fail to load
- **THEN** the system treats the master switch as disabled

### Requirement: Theme authors can integrate visitor applications
The system SHALL document and expose the complete same-origin, CSRF-protected, CAPTCHA-protected
HTML form contract required by Halo themes.

#### Scenario: Theme evaluates form availability
- **WHEN** the links template is rendered
- **THEN** the model exposes `linkApplicationEnabled`
- **AND** its value is true only when the master and visitor-submission switches are enabled
- **AND** a true value means the theme must render the built-in CAPTCHA

#### Scenario: Theme renders a protected form
- **WHEN** a theme renders the application form
- **THEN** the documented example loads its image from `GET /links/captcha`
- **AND** includes the `_csrf` hidden field, required `captchaCode`, and supported application fields
- **AND** submits `application/x-www-form-urlencoded` to `/links/apply`

#### Scenario: Theme works without JavaScript
- **WHEN** a theme uses a plain HTML form and image
- **THEN** the challenge is associated through an HttpOnly cookie
- **AND** the visitor can complete and submit the form without JavaScript

#### Scenario: Theme refreshes the CAPTCHA
- **WHEN** the page loads, a CAPTCHA error redirects back to `/links`, or optional theme JavaScript
  requests the image again
- **THEN** the browser receives a new image and challenge cookie
- **AND** the previous cookie value is overwritten

#### Scenario: Theme submits with JavaScript
- **WHEN** a theme implements the form with same-origin JavaScript
- **THEN** the documentation provides a form-encoded example using the same CAPTCHA, CSRF, and
  redirect contract
- **AND** does not require a JSON application API

#### Scenario: Theme handles result redirects
- **WHEN** submission succeeds, fails CAPTCHA or field validation, is rate limited, is duplicated,
  or is disabled
- **THEN** the documentation defines the corresponding `applied`, `field`, `value`, and `message`
  query parameters
- **AND** explains that CAPTCHA failures do not return submitted field values

#### Scenario: Initial visitor application theme contract
- **WHEN** a theme exposes the visitor application feature
- **THEN** it must render the CAPTCHA image and submit `captchaCode`
- **AND** the plugin does not provide a legacy form, compatibility branch, or fallback

#### Scenario: Theme presents the visual challenge
- **WHEN** a theme renders the image-only CAPTCHA
- **THEN** its integration guidance provides explanatory text and a keyboard-operable refresh
  control
- **AND** documents that no audio or non-visual challenge is provided in this version

### Requirement: Anonymous users can submit link applications
The system SHALL allow visitors to submit link applications via an HTML form POST to
`/links/apply` only while both the application master switch and visitor-submission switch are
enabled and only after the request passes CSRF, CAPTCHA, and submission-rate checks.

#### Scenario: Successful submission
- **WHEN** application and visitor submission are enabled
- **AND** a visitor submits a form with `url`, `displayName`, `_csrf`, and a valid `captchaCode` to
  `/links/apply`
- **THEN** the system creates a `LinkApplication` with status `PENDING` and origin type `FORM`
- **AND** redirects the browser to `/links?applied=success`

#### Scenario: Authenticated visitor uses the public form
- **WHEN** an authenticated visitor submits through `/links/apply`
- **THEN** the request requires the same valid CAPTCHA as an anonymous submission

#### Scenario: Submission with optional fields
- **WHEN** application and visitor submission are enabled
- **AND** a CAPTCHA-validated visitor submits `url`, `displayName`, `logo`, `description`, `email`,
  `backlink`, and `feedUrls`, where every provided URL uses HTTP or HTTPS
- **THEN** the system stores all provided fields in the `LinkApplication`
- **AND** redirects the browser to `/links?applied=success`

#### Scenario: Missing or invalid CSRF token
- **WHEN** a visitor submits a correct CAPTCHA without a valid CSRF token
- **THEN** Halo's CSRF protection rejects the request
- **AND** the system does not create a LinkApplication

#### Scenario: Missing or invalid CAPTCHA
- **WHEN** a visitor submits a missing, malformed, incorrect, expired, or replayed CAPTCHA
- **THEN** the system does not call the shared LinkApplication creation service
- **AND** does not consume the formal application submission allowance
- **AND** redirects to
  `/links?applied=error&field=captchaCode&message=验证码错误或已过期，请重新输入`

#### Scenario: CAPTCHA failure protects submitted values
- **WHEN** CAPTCHA validation fails
- **THEN** the redirect does not include the CAPTCHA answer in `value`
- **AND** does not include the other submitted application fields
- **AND** the plugin does not persist a failed application draft

#### Scenario: Existing formal Link rejects submission
- **WHEN** a CAPTCHA-validated visitor submits a `url` whose canonical comparison key matches an
  existing formal Link
- **THEN** the system rejects the submission as a duplicate

#### Scenario: Active application rejects submission
- **WHEN** a CAPTCHA-validated visitor submits a `url` whose canonical comparison key matches a
  `PENDING`, `APPROVING`, or `APPROVED` LinkApplication from any source
- **THEN** the system rejects the submission as a duplicate

#### Scenario: Rejected form application blocks resubmission
- **WHEN** a CAPTCHA-validated visitor submits a `url` whose canonical comparison key matches a
  `REJECTED` form-origin LinkApplication
- **THEN** the system rejects the submission
- **AND** redirects the browser to
  `/links?applied=error&field=url&message=该链接已提交申请`

#### Scenario: Rejected comment application allows form submission
- **WHEN** a CAPTCHA-validated visitor submits a `url` whose canonical comparison key matches only a
  `REJECTED` Comment-origin LinkApplication
- **THEN** the system allows the new form application when all other validation succeeds

#### Scenario: Rate limit exceeded
- **WHEN** a visitor submits a second CAPTCHA-validated request from the same IP within 1 minute
- **THEN** the system rejects the submission
- **AND** redirects the browser to
  `/links?applied=error&message=提交过于频繁，请稍后再试`

#### Scenario: Concurrent rate-limit checks use the same IP
- **WHEN** two requests with independently valid CAPTCHAs from the same IP reach the submission rate
  limiter concurrently
- **THEN** the limiter atomically allows at most one request in the one-minute window
- **AND** expired IP entries are eligible for bounded cleanup

#### Scenario: Invalid URL format
- **WHEN** a CAPTCHA-validated visitor submits a form with an invalid URL format
- **THEN** the system rejects the submission
- **AND** redirects the browser to
  `/links?applied=error&field=url&value=<submitted-value>&message=URL格式错误`

#### Scenario: Invalid optional URL format
- **WHEN** a CAPTCHA-validated visitor submits a non-HTTP/HTTPS `logo`, `backlink`, or `feedUrls`
  value
- **THEN** the system rejects the submission
- **AND** identifies the invalid optional field in the redirect

#### Scenario: Missing required fields
- **WHEN** a CAPTCHA-validated visitor submits a form without `url` or `displayName`
- **THEN** the system rejects the submission
- **AND** redirects the browser to
  `/links?applied=error&field=<missing-field>&message=必填字段不能为空`

#### Scenario: Form value回填 on non-CAPTCHA validation error
- **WHEN** a CAPTCHA-validated submission fails with a field validation error
- **THEN** the redirect URL includes `applied=error`, the failing `field`, the submitted `value`, and
  an error `message`
- **AND** the theme template can use these query parameters to repopulate the form and show error
  messages

## ADDED Requirements

### Requirement: Visitor CAPTCHA images use a fixed first-party format
The system SHALL generate the visitor-application CAPTCHA inside plugin-links without an external
provider or administrator CAPTCHA configuration.

#### Scenario: CAPTCHA image is generated
- **WHEN** an admitted request reaches `GET /links/captcha` while visitor submission is enabled
- **THEN** the system returns a `160 x 48` PNG containing five alphanumeric characters
- **AND** excludes ambiguous characters
- **AND** derives the answer and visual variation from a cryptographically secure random source
- **AND** uses a packaged, openly licensed font

#### Scenario: CAPTCHA comparison is normalized
- **WHEN** the visitor submits an answer
- **THEN** the system trims it and rejects it unless exactly five ASCII characters remain
- **AND** compares a correctly sized answer without case sensitivity

#### Scenario: CAPTCHA response is not cached
- **WHEN** an image is generated successfully
- **THEN** the response uses `Content-Type: image/png`
- **AND** sets `Cache-Control: no-store, no-cache, must-revalidate`

#### Scenario: Visitor submission is disabled
- **WHEN** either the application master switch or visitor-submission switch is disabled
- **AND** a client requests `GET /links/captcha`
- **THEN** the endpoint returns `404`
- **AND** does not create challenge state

### Requirement: Visitor CAPTCHA challenges are short-lived and single-use
The system SHALL associate each image with one bounded process-local challenge that can be consumed
by at most one verification attempt.

#### Scenario: Challenge cookie is issued
- **WHEN** an image is generated successfully
- **THEN** the response writes an opaque challenge identifier to a plugin-specific cookie
- **AND** the cookie has path `/links`, a five-minute maximum age, `HttpOnly`, and `SameSite=Lax`
- **AND** sets `Secure` when the effective request scheme is HTTPS
- **AND** does not bind the challenge to IP, authentication, session, or User-Agent

#### Scenario: A new image replaces the browser challenge
- **WHEN** the same cookie jar successfully requests another image
- **THEN** the store invalidates the challenge identified by the previous cookie
- **AND** the new challenge identifier overwrites that cookie
- **AND** only the newest cookie-associated challenge can be submitted from that cookie jar

#### Scenario: Challenge expires
- **WHEN** five minutes have elapsed since challenge issuance
- **THEN** verification treats the challenge as invalid
- **AND** no application is created

#### Scenario: Verification consumes the challenge
- **WHEN** a request attempts CAPTCHA verification
- **THEN** the store atomically removes the challenge before comparing the answer
- **AND** expires the challenge cookie
- **AND** the same challenge cannot be attempted again regardless of the result

#### Scenario: Concurrent verification replays one challenge
- **WHEN** multiple requests concurrently submit the same challenge and correct answer
- **THEN** at most one request passes CAPTCHA verification
- **AND** all other requests receive the generic CAPTCHA error

#### Scenario: Challenge state is cleaned
- **WHEN** generation or verification accesses the challenge store
- **THEN** expired entries are eligible for lazy removal
- **AND** no periodic cleanup task is required

#### Scenario: Plugin stops
- **WHEN** the plugin instance stops
- **THEN** all outstanding challenge state is discarded

### Requirement: CAPTCHA generation is resource-bounded
The system SHALL bound image issuance, outstanding state, and drawing concurrency independently from
the formal application submission limiter.

#### Scenario: Per-IP generation limit is exceeded
- **WHEN** one remote IP requests more than 10 CAPTCHA images within one minute
- **THEN** the image endpoint returns `429 Too Many Requests`
- **AND** provides `Retry-After`
- **AND** does not draw an image or create a challenge

#### Scenario: Generation limiter state is bounded
- **WHEN** generation requests arrive from distinct remote IPs
- **THEN** the limiter tracks at most 10,000 IP entries
- **AND** expired entries are eligible for bounded cleanup
- **AND** the oldest remaining tracking entry is eligible for removal when a new IP arrives at
  capacity
- **AND** the plugin does not parse or trust forwarding headers itself

#### Scenario: Challenge capacity is available
- **WHEN** fewer than 10,000 unexpired challenges exist
- **THEN** an otherwise admitted image request may create one challenge
- **AND** stores only its normalized answer and expiry, not image bytes

#### Scenario: Challenge capacity is exhausted
- **WHEN** lazy cleanup completes and 10,000 unexpired challenges remain
- **THEN** the image endpoint returns `503 Service Unavailable`
- **AND** does not evict an unexpired challenge

#### Scenario: Drawing concurrency is available
- **WHEN** fewer than four image renderings are active
- **THEN** an admitted request may render off the reactive event loop

#### Scenario: Drawing concurrency is exhausted
- **WHEN** four image renderings are active
- **THEN** another generation request immediately returns `503 Service Unavailable`
- **AND** does not enter an unbounded drawing queue

#### Scenario: Image generation fails
- **WHEN** font loading, drawing, encoding, or challenge storage fails
- **THEN** the image endpoint returns `503 Service Unavailable`
- **AND** POST verification remains fail closed

#### Scenario: Image generation does not consume submission allowance
- **WHEN** a visitor loads or refreshes CAPTCHA images
- **THEN** those requests do not consume the existing one-per-IP-per-minute application submission
  allowance

### Requirement: CAPTCHA processing minimizes sensitive data exposure
The system SHALL keep challenge secrets and failed form data out of persistent resources,
redirects, and routine logs.

#### Scenario: Challenge is created
- **WHEN** the system issues a CAPTCHA
- **THEN** it does not persist the answer, identifier, cookie, or image in a LinkApplication or other
  extension

#### Scenario: CAPTCHA verification fails normally
- **WHEN** a visitor submits an invalid CAPTCHA
- **THEN** the system does not log the answer, identifier, cookie, application fields, or raw IP
- **AND** does not write a routine per-attempt failure log

#### Scenario: CAPTCHA system fails
- **WHEN** capacity, rendering, or storage produces an operational failure
- **THEN** the system may log aggregate diagnostic context
- **AND** does not log challenge secrets, submitted form data, or raw client IPs
