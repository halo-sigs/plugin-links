## ADDED Requirements

### Requirement: Visitor submission responses are content negotiated
The system SHALL select the response representation for `POST /links/apply` from the request's
acceptable response media types while preserving `application/x-www-form-urlencoded` as the only
supported request body.

#### Scenario: JavaScript explicitly prefers JSON
- **WHEN** a same-origin client submits a valid form request
- **AND** `application/json` has a positive quality and is preferred over `text/html`
- **THEN** the system returns the JSON representation of the submission result
- **AND** does not redirect to `/links`

#### Scenario: Client does not declare a response preference
- **WHEN** a client submits a valid form request without an `Accept` header
- **THEN** the system returns the existing `303` redirect representation

#### Scenario: Client accepts any representation
- **WHEN** a client submits a valid form request with `Accept: */*`
- **THEN** the system returns the existing `303` redirect representation

#### Scenario: Client prefers HTML
- **WHEN** a client submits a valid form request and prefers `text/html` over `application/json`
- **THEN** the system returns the existing `303` redirect representation

#### Scenario: Client gives HTML and JSON equal preference
- **WHEN** a client submits a valid form request and gives `text/html` and `application/json` equal
  preference
- **THEN** the system returns the existing `303` redirect representation

#### Scenario: Client accepts no supported response representation
- **WHEN** a client submits to `/links/apply` but accepts neither HTML nor JSON
- **THEN** the system returns `406 Not Acceptable`
- **AND** does not promise a plugin JSON envelope
- **AND** does not process an application submission

#### Scenario: JSON client sends an unsupported request media type
- **WHEN** a client explicitly prefers JSON
- **AND** submits to `/links/apply` with a request media type other than
  `application/x-www-form-urlencoded`
- **THEN** the system returns `415 Unsupported Media Type`
- **AND** returns `status=error`, `code=UNSUPPORTED_MEDIA_TYPE`, and a display `message` in the JSON
  envelope
- **AND** does not process an application submission

#### Scenario: HTML client sends an unsupported request media type
- **WHEN** a client does not explicitly prefer JSON
- **AND** submits to `/links/apply` with a request media type other than
  `application/x-www-form-urlencoded`
- **THEN** the system returns `415 Unsupported Media Type`
- **AND** does not promise a plugin JSON envelope
- **AND** does not process an application submission

#### Scenario: Negotiated responses identify their selection input
- **WHEN** `/links/apply` returns either the HTML redirect or JSON representation
- **THEN** the response includes `Vary: Accept`
- **AND** a JSON response also includes `Cache-Control: no-store`

### Requirement: Visitor JSON results use a stable envelope
The system SHALL return plugin-owned JSON results with a stable status, code, display message, and
an optional form field without reflecting submitted values or exposing application resources.

#### Scenario: Successful JSON envelope
- **WHEN** a visitor application succeeds in JSON mode
- **THEN** the body contains `status=success`, `code=APPLICATION_CREATED`, and
  `message=申请提交成功`
- **AND** the body omits `field`

#### Scenario: Field-specific JSON envelope
- **WHEN** a JSON result applies to a form field
- **THEN** the body contains `status=error`, the stable result `code`, the original form-field name
  in `field`, and a display `message`

#### Scenario: Global JSON error envelope
- **WHEN** a JSON result does not apply to one form field
- **THEN** the body contains `status=error`, the stable result `code`, and a display `message`
- **AND** the body omits `field` instead of returning a null field

#### Scenario: JSON results do not reflect or expose application data
- **WHEN** the system returns any JSON submission result
- **THEN** the body does not contain a submitted `value`
- **AND** does not contain a generic `data` member
- **AND** does not expose a LinkApplication metadata name or resource representation

#### Scenario: Result codes evolve compatibly
- **WHEN** a result code has been published in the theme API
- **THEN** later compatible versions do not remove it or change its meaning
- **AND** later versions may add new result codes
- **AND** the documentation requires themes to show a global fallback for unknown codes or fields

#### Scenario: Display messages are not program identifiers
- **WHEN** a theme handles a JSON result
- **THEN** it uses `code` rather than the Chinese `message` for program branching
- **AND** it can present `message` directly as display text

### Requirement: Visitor JSON results use outcome-specific HTTP semantics
The system SHALL map every plugin-owned visitor submission outcome to its defined HTTP status, code,
message, and optional field without changing application processing or side-effect ordering.

#### Scenario: JSON application is created
- **WHEN** a JSON-negotiated submission creates a `PENDING` LinkApplication
- **THEN** the system returns `201 Created`
- **AND** returns `status=success` and `code=APPLICATION_CREATED`

#### Scenario: JSON submission is disabled
- **WHEN** effective application settings disable visitor submission
- **AND** the client explicitly prefers JSON
- **THEN** the system returns `403 Forbidden`
- **AND** returns `status=error` and `code=APPLICATION_DISABLED`
- **AND** preserves the existing fail-closed settings behavior

#### Scenario: JSON CAPTCHA validation fails
- **WHEN** a JSON-negotiated submission has a missing, malformed, incorrect, expired, or replayed
  CAPTCHA
- **THEN** the system returns `422 Unprocessable Content`
- **AND** returns `status=error`, `code=INVALID_CAPTCHA`, and `field=captchaCode`
- **AND** does not reflect any submitted value

#### Scenario: JSON field validation fails
- **WHEN** a JSON-negotiated submission fails required-field or URL validation
- **THEN** the system returns `422 Unprocessable Content`
- **AND** returns `status=error`, `code=VALIDATION_FAILED`, and the failing form-field name

#### Scenario: JSON submission is duplicated
- **WHEN** a JSON-negotiated submission is rejected by existing duplicate rules
- **THEN** the system returns `409 Conflict`
- **AND** returns `status=error`, `code=DUPLICATE_APPLICATION`, and `field=url`

#### Scenario: JSON submission is rate limited
- **WHEN** a JSON-negotiated submission exceeds the existing per-IP submission limit
- **THEN** the system returns `429 Too Many Requests`
- **AND** returns `status=error` and `code=RATE_LIMITED`
- **AND** includes `Retry-After` with the accurate positive whole seconds until the IP can retry

#### Scenario: HTML submission is rate limited
- **WHEN** a submission that does not explicitly prefer JSON exceeds the per-IP submission limit
- **THEN** the system preserves the existing `303` redirect
- **AND** does not add a `Retry-After` header that could be interpreted as delaying the redirected
  request

#### Scenario: JSON submission reaches pending capacity
- **WHEN** a JSON-negotiated submission passes existing security, duplicate, and validation checks
- **AND** pending capacity is exhausted
- **THEN** the system returns `409 Conflict`
- **AND** returns `status=error` and `code=CAPACITY_REACHED`
- **AND** does not expose the configured capacity

#### Scenario: JSON submission is operationally unavailable
- **WHEN** creation, persistence, or authoritative capacity evaluation fails after visitor abuse
  controls pass
- **AND** the client explicitly prefers JSON
- **THEN** the system returns `503 Service Unavailable`
- **AND** returns `status=error` and `code=APPLICATION_UNAVAILABLE`

#### Scenario: Notification failure follows persistence
- **WHEN** a JSON-negotiated submission persists a LinkApplication
- **AND** publishing its notification fails
- **THEN** the system still returns the successful `201 APPLICATION_CREATED` result

#### Scenario: JSON representation preserves processing order
- **WHEN** a client explicitly prefers JSON
- **THEN** settings, CSRF, CAPTCHA, rate limiting, validation, duplicate detection, capacity,
  persistence, and notification handling retain their existing ordering and side effects
- **AND** response negotiation does not create an alternate business workflow

#### Scenario: Platform security failure is outside the JSON envelope
- **WHEN** Halo Security rejects a submission before the route, including for invalid CSRF
- **THEN** Halo retains its native response representation
- **AND** the plugin does not promise its JSON envelope for that response

#### Scenario: Asynchronous client receives a non-envelope failure
- **WHEN** a theme receives `406`, a Halo platform response, a non-JSON response, or a network error
- **THEN** the integration guidance requires a theme-owned generic fallback
- **AND** does not instruct the theme to parse arbitrary error response text as a plugin result

## MODIFIED Requirements

### Requirement: Theme authors can integrate visitor applications
The system SHALL document and expose the complete same-origin, CSRF-protected, CAPTCHA-protected
HTML form contract and negotiated asynchronous JSON response contract required by Halo themes.

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
- **AND** the existing `303` result contract remains unchanged

#### Scenario: Theme refreshes the CAPTCHA
- **WHEN** the page loads, a CAPTCHA error redirects back to `/links`, or optional theme JavaScript
  requests the image again
- **THEN** the browser receives a new image and challenge cookie
- **AND** the previous cookie value is overwritten

#### Scenario: Theme submits with JavaScript
- **WHEN** a theme implements the form with same-origin JavaScript
- **THEN** the documentation provides a complete form-encoded example that explicitly requests
  `application/json`
- **AND** the example uses the same CAPTCHA and CSRF contract as an HTML form
- **AND** the endpoint does not require a JSON request body

#### Scenario: Theme handles result redirects
- **WHEN** a non-JSON submission succeeds, fails CAPTCHA or field validation, is rate limited, is
  duplicated, reaches capacity, is unavailable, or is disabled
- **THEN** the documentation defines the corresponding `applied`, `field`, `value`, and `message`
  query parameters
- **AND** explains that CAPTCHA failures do not return submitted field values

#### Scenario: Theme handles JSON results
- **WHEN** a JavaScript submission receives the plugin JSON envelope
- **THEN** the documented example branches on stable `code`
- **AND** renders `message` using a text-safe DOM API
- **AND** associates a known `field` with its form control and otherwise uses a global message
- **AND** handles an unknown code with a global fallback

#### Scenario: Theme handles non-JSON asynchronous failures
- **WHEN** an asynchronous submission receives a response that is not the plugin JSON envelope or
  fails at the network layer
- **THEN** the documented example presents a theme-owned generic failure
- **AND** does not parse a redirect URL or arbitrary response text for a result

#### Scenario: Theme prevents repeated asynchronous submission
- **WHEN** a JavaScript submission is pending
- **THEN** the documented example disables repeated submission until the request settles
- **AND** the public endpoint does not require an idempotency key

#### Scenario: Theme refreshes CAPTCHA after asynchronous business failure
- **WHEN** JSON mode reports invalid CAPTCHA, rate limiting, duplication, field validation, pending
  capacity, or operational unavailability
- **THEN** the documented example loads a new CAPTCHA before another attempt
- **AND** a successful result closes or resets the form

#### Scenario: Theme chooses how to expose template state to JavaScript
- **WHEN** theme JavaScript needs `csrfToken` or `linkApplicationEnabled`
- **THEN** the documentation identifies the existing template variables
- **AND** does not mandate a `meta`, `data-*`, hidden-input, inline-script, or new API strategy

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
enabled and only after the request passes CSRF, CAPTCHA, and submission-rate checks. Redirect
outcomes in this requirement apply when the request does not explicitly prefer JSON; the negotiated
JSON equivalents are defined by the visitor JSON result requirements.

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

### Requirement: Visitor submissions report pending capacity outcomes
The system SHALL preserve visitor security checks and duplicate behavior before reporting pending
capacity outcomes from `/links/apply`. Redirect outcomes in this requirement apply when the request
does not explicitly prefer JSON; the negotiated JSON equivalents are defined by the visitor JSON
result requirements.

#### Scenario: Visitor submission reaches full capacity
- **WHEN** a visitor passes CAPTCHA and the IP submission limit with otherwise valid,
  non-duplicate input
- **AND** pending capacity is exhausted
- **THEN** the system does not persist a LinkApplication
- **AND** redirects to
  `/links?applied=error&message=待审核申请数量已达上限，请稍后再试`
- **AND** the redirect does not include `field`, `value`, or the configured capacity

#### Scenario: Duplicate submission reaches full capacity
- **WHEN** a visitor submission is both a duplicate under existing source-aware rules and received
  while pending capacity is exhausted
- **THEN** the system returns the existing duplicate result instead of the capacity-reached result

#### Scenario: Full-capacity submission consumes abuse controls
- **WHEN** a valid CAPTCHA and IP submission allowance reach authoritative creation while capacity
  is full
- **THEN** the CAPTCHA remains consumed
- **AND** the IP submission allowance remains consumed

#### Scenario: Capacity is full while the theme renders
- **WHEN** the application master and visitor-submission switches are enabled while capacity is full
- **THEN** the template model continues to expose `linkApplicationEnabled` as true
- **AND** the CAPTCHA endpoint remains available according to its existing security limits

#### Scenario: Visitor capacity evaluation fails
- **WHEN** authoritative capacity evaluation fails after visitor abuse controls pass
- **THEN** the system does not persist a LinkApplication
- **AND** redirects to `/links?applied=error&message=暂时无法提交，请稍后再试`
- **AND** the redirect does not include `field` or `value`

#### Scenario: Full capacity is handled normally
- **WHEN** visitor creation returns the capacity-reached result
- **THEN** the system does not emit a warning or error log for that normal result

#### Scenario: Capacity evaluation fails operationally
- **WHEN** visitor capacity evaluation fails because settings or storage are unavailable
- **THEN** the system records an operational diagnostic
- **AND** the diagnostic excludes application fields and other attacker-controlled values
