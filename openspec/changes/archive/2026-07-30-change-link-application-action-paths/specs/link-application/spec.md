## ADDED Requirements

### Requirement: Obsolete visitor application action paths are removed
The system SHALL expose visitor application submission and CAPTCHA generation only at their new
action paths and SHALL NOT retain the previous paths as aliases or redirects.

#### Scenario: Previous submission path is not exposed
- **WHEN** a client sends `POST /links/apply`
- **THEN** the plugin does not route the request to the visitor application submission handler

#### Scenario: Previous CAPTCHA path is not exposed
- **WHEN** a client sends `GET /links/captcha`
- **THEN** the plugin does not route the request to the visitor CAPTCHA handler

## MODIFIED Requirements

### Requirement: Link applications record their origin
The system SHALL require origin information on every LinkApplication resource.

#### Scenario: Form application records origin
- **WHEN** an anonymous visitor successfully submits `/links/apply/submit`
- **THEN** the created LinkApplication has origin type `FORM`

#### Scenario: Comment application records recognition origin
- **WHEN** comment recognition creates a LinkApplication
- **THEN** the application has origin type `COMMENT`
- **AND** records its source Comment metadata name as `origin.comment.name`

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
- **AND** a visitor posts a valid form to `/links/apply/submit`
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
- **AND** every request through `/links/apply/submit` requires a valid CAPTCHA
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
HTML form contract and negotiated asynchronous JSON response contract required by Halo themes.

#### Scenario: Theme evaluates form availability
- **WHEN** the links template is rendered
- **THEN** the model exposes `linkApplicationEnabled`
- **AND** its value is true only when the master and visitor-submission switches are enabled
- **AND** a true value means the theme must render the built-in CAPTCHA

#### Scenario: Theme renders a protected form
- **WHEN** a theme renders the application form
- **THEN** the documented example loads its image from `GET /links/apply/captcha`
- **AND** includes the `_csrf` hidden field, required `captchaCode`, and supported application fields
- **AND** submits `application/x-www-form-urlencoded` to `/links/apply/submit`

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
`/links/apply/submit` only while both the application master switch and visitor-submission switch
are enabled and only after the request passes CSRF, CAPTCHA, and submission-rate checks. Redirect
outcomes in this requirement apply when the request does not explicitly prefer JSON; the negotiated
JSON equivalents are defined by the visitor JSON result requirements.

#### Scenario: Successful submission
- **WHEN** application and visitor submission are enabled
- **AND** a visitor submits a form with `url`, `displayName`, `_csrf`, and a valid `captchaCode` to
  `/links/apply/submit`
- **THEN** the system creates a `LinkApplication` with status `PENDING` and origin type `FORM`
- **AND** redirects the browser to `/links?applied=success`

#### Scenario: Authenticated visitor uses the public form
- **WHEN** an authenticated visitor submits through `/links/apply/submit`
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
capacity outcomes from `/links/apply/submit`. Redirect outcomes in this requirement apply when the
request does not explicitly prefer JSON; the negotiated JSON equivalents are defined by the visitor
JSON result requirements.

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

### Requirement: Visitor CAPTCHA images use a fixed first-party format
The system SHALL generate the visitor-application CAPTCHA inside plugin-links without an external
provider or administrator CAPTCHA configuration.

#### Scenario: CAPTCHA image is generated
- **WHEN** an admitted request reaches `GET /links/apply/captcha` while visitor submission is
  enabled
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
- **AND** a client requests `GET /links/apply/captcha`
- **THEN** the endpoint returns `404`
- **AND** does not create challenge state

### Requirement: Visitor submission responses are content negotiated
The system SHALL select the response representation for `POST /links/apply/submit` from the
request's acceptable response media types while preserving `application/x-www-form-urlencoded` as
the only supported request body.

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
- **WHEN** a client submits to `/links/apply/submit` but accepts neither HTML nor JSON
- **THEN** the system returns `406 Not Acceptable`
- **AND** does not promise a plugin JSON envelope
- **AND** does not process an application submission

#### Scenario: JSON client sends an unsupported request media type
- **WHEN** a client explicitly prefers JSON
- **AND** submits to `/links/apply/submit` with a request media type other than
  `application/x-www-form-urlencoded`
- **THEN** the system returns `415 Unsupported Media Type`
- **AND** returns `status=error`, `code=UNSUPPORTED_MEDIA_TYPE`, and a display `message` in the JSON
  envelope
- **AND** does not process an application submission

#### Scenario: HTML client sends an unsupported request media type
- **WHEN** a client does not explicitly prefer JSON
- **AND** submits to `/links/apply/submit` with a request media type other than
  `application/x-www-form-urlencoded`
- **THEN** the system returns `415 Unsupported Media Type`
- **AND** does not promise a plugin JSON envelope
- **AND** does not process an application submission

#### Scenario: Negotiated responses identify their selection input
- **WHEN** `/links/apply/submit` returns either the HTML redirect or JSON representation
- **THEN** the response includes `Vary: Accept`
- **AND** a JSON response also includes `Cache-Control: no-store`
