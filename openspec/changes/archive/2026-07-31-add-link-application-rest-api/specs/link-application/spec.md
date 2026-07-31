## ADDED Requirements

### Requirement: Native Form submission has one redirect representation
The system SHALL keep `POST /links/apply/submit` as an
`application/x-www-form-urlencoded`-only transport whose plugin-owned business outcomes use the
existing redirect contract.

#### Scenario: Form client declares any Accept preference
- **WHEN** a client submits a supported native Form request with any `Accept` header or no
  `Accept` header
- **THEN** the plugin processes the request without negotiating its response representation
- **AND** returns the existing `303` success or error redirect for its business outcome

#### Scenario: Form client asks for JSON
- **WHEN** a client submits a supported native Form request with `Accept: application/json`
- **THEN** the plugin does not return the removed JSON envelope
- **AND** does not return `406 Not Acceptable`
- **AND** returns the same redirect contract as other native Form clients

#### Scenario: Form request media type is unsupported
- **WHEN** a client posts to `/links/apply/submit` with a request media type other than
  `application/x-www-form-urlencoded`
- **THEN** the endpoint returns `415 Unsupported Media Type`
- **AND** does not process an application

#### Scenario: Form responses no longer vary on Accept
- **WHEN** `/links/apply/submit` returns a plugin-owned response
- **THEN** it does not add `Vary: Accept`
- **AND** it does not expose an Accept-driven JSON representation

## MODIFIED Requirements

### Requirement: Link applications record their origin
The system SHALL require origin information on every LinkApplication resource and SHALL treat both
public visitor transports as `FORM` origin.

#### Scenario: Native Form application records origin
- **WHEN** an anonymous visitor successfully submits `/links/apply/submit`
- **THEN** the created LinkApplication has origin type `FORM`

#### Scenario: REST application records Form origin
- **WHEN** an anonymous visitor successfully creates an application through
  `/apis/api.link.halo.run/v1alpha1/link-applications`
- **THEN** the created LinkApplication has origin type `FORM`
- **AND** the system does not add a distinct API origin type

#### Scenario: Comment application records recognition origin
- **WHEN** comment recognition creates a LinkApplication
- **THEN** the application has origin type `COMMENT`
- **AND** records its source Comment metadata name as `origin.comment.name`

### Requirement: Link application settings govern new application creation
The system SHALL provide a disabled-by-default Link Application settings group whose master switch
controls all new LinkApplication creation channels and SHALL require the built-in image CAPTCHA
whenever either public visitor submission transport is effective.

#### Scenario: Application feature is disabled by default
- **WHEN** the plugin has no Link Application settings
- **THEN** the application master switch is treated as disabled
- **AND** neither native Form, REST, nor Comment recognition creates a LinkApplication

#### Scenario: Master switch disables native Form submissions
- **WHEN** the application master switch is disabled
- **AND** a visitor posts a valid form to `/links/apply/submit`
- **THEN** the system does not create a LinkApplication
- **AND** redirects to `/links?applied=disabled&message=友链申请功能暂未开放`

#### Scenario: Master switch disables REST submissions
- **WHEN** the application master switch is disabled
- **AND** a visitor posts an otherwise valid request to the REST application resource
- **THEN** the system does not create a LinkApplication
- **AND** returns the REST disabled Problem Detail

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
- **THEN** native Form and REST visitor creation are controlled by the same visitor-submission child
  switch
- **AND** Comment recognition is controlled by its own child switch, model, and source settings

#### Scenario: Visitor submission automatically requires CAPTCHA
- **WHEN** the master switch and visitor-submission child switch are enabled
- **THEN** the native Form and REST CAPTCHA operations are available
- **AND** every request through either public visitor submission transport requires a valid
  CAPTCHA
- **AND** no independent CAPTCHA setting or compatibility fallback is exposed

#### Scenario: CAPTCHA does not govern other creation channels
- **WHEN** Console, Comment recognition, or an internal service creates a LinkApplication
- **THEN** CAPTCHA verification is not required
- **AND** the channel keeps its existing settings and validation behavior

#### Scenario: Application settings cannot be loaded
- **WHEN** application settings are missing or fail to load
- **THEN** the system treats the master switch as disabled

### Requirement: Single-instance application creation is concurrency-safe
The system SHALL serialize the complete duplicate-check and create operation by canonical URL within
one plugin instance for native Form, REST, and Comment creation.

#### Scenario: Concurrent visitor submissions use the same canonical URL
- **WHEN** two visitor requests through either public transport use the same canonical URL
- **THEN** at most one request creates a `PENDING` LinkApplication
- **AND** the other request receives its transport's duplicate response

#### Scenario: Native Form and REST submissions race
- **WHEN** native Form and REST submissions with the same canonical URL are processed concurrently
  in one plugin instance
- **THEN** at most one `FORM`-origin LinkApplication is created
- **AND** both requests use the same source-aware duplicate rules

#### Scenario: Visitor submission and Comment recognition race for the same URL
- **WHEN** either public visitor transport and positive Comment recognition use the same canonical
  URL concurrently in one plugin instance
- **THEN** at most one LinkApplication is created
- **AND** source-aware duplicate rules are applied after entering the serialized operation

#### Scenario: Serialized creation finishes
- **WHEN** a serialized create operation succeeds or fails
- **THEN** its process-local coordination entry is released
- **AND** completed URL keys do not accumulate without bound

### Requirement: Theme authors can integrate visitor applications
The system SHALL document the native same-origin, CSRF-protected, Cookie-CAPTCHA Form contract and
the separate cookie-free REST contract required by browser and general HTTP integrations.

#### Scenario: Theme evaluates form availability
- **WHEN** the links template is rendered
- **THEN** the model exposes `linkApplicationEnabled`
- **AND** its value is true only when the master and visitor-submission switches are enabled
- **AND** a true value means the theme must render the built-in CAPTCHA for a native Form

#### Scenario: Theme renders a protected native Form
- **WHEN** a theme renders the native application form
- **THEN** the documented example loads its image from `GET /links/apply/captcha`
- **AND** includes the `_csrf` hidden field, required `captchaCode`, and all supported application
  fields
- **AND** submits `application/x-www-form-urlencoded` to `/links/apply/submit`

#### Scenario: Native Form works without JavaScript
- **WHEN** a theme uses a plain HTML form and image
- **THEN** the challenge is associated through an HttpOnly cookie
- **AND** the visitor can complete and submit the form without JavaScript
- **AND** every plugin-owned result uses the existing `303` redirect contract

#### Scenario: Theme refreshes the native Form CAPTCHA
- **WHEN** the page loads, a CAPTCHA error redirects back to `/links`, or optional theme JavaScript
  requests the image again
- **THEN** the browser receives a new image and challenge cookie
- **AND** the previous cookie value is overwritten

#### Scenario: Theme submits asynchronously
- **WHEN** a theme implements visitor submission with JavaScript
- **THEN** the documentation directs it to the REST CAPTCHA and application operations
- **AND** uses JSON with an explicit challenge identifier and no CSRF token or cookie
- **AND** does not instruct it to request JSON from `/links/apply/submit`

#### Scenario: Theme handles native Form result redirects
- **WHEN** a native Form submission succeeds, fails CAPTCHA or field validation, is rate limited, is
  duplicated, reaches capacity, is unavailable, or is disabled
- **THEN** the documentation defines the corresponding `applied`, `field`, `value`, and `message`
  query parameters
- **AND** explains that CAPTCHA failures do not return submitted field values

#### Scenario: Theme handles REST results
- **WHEN** a JavaScript submission receives a REST response
- **THEN** the documented example handles the `201` created result
- **AND** reads Problem Details `status` and `type` for failures
- **AND** treats `detail` as display text rather than a program identifier
- **AND** leaves presentation and interaction-specific error handling to the theme

#### Scenario: Theme owns REST submission interaction
- **WHEN** a theme integrates the REST application resource
- **THEN** the documentation does not prescribe button state, repeated-submission guards, messages,
  or form lifecycle
- **AND** the public endpoint still does not provide an idempotency key

#### Scenario: Theme refreshes REST CAPTCHA after failure
- **WHEN** a REST application attempt fails after presenting a challenge
- **THEN** the documentation explains that another attempt requires a new REST CAPTCHA
- **AND** the theme decides how to refresh the challenge and handle successful submission

#### Scenario: Theme chooses how to expose template state to JavaScript
- **WHEN** theme JavaScript needs `linkApplicationEnabled`
- **THEN** the documentation identifies the existing template variable
- **AND** does not mandate a `meta`, `data-*`, hidden-input, inline-script, or new availability API
  strategy

#### Scenario: Initial visitor application theme contract
- **WHEN** a theme exposes either visitor application transport
- **THEN** it must present the built-in CAPTCHA required by that transport
- **AND** the plugin does not provide a legacy form, compatibility branch, or fallback

#### Scenario: Theme presents the visual challenge
- **WHEN** a theme renders the image-only CAPTCHA
- **THEN** the documentation states that no audio or non-visual challenge is provided in this
  version
- **AND** leaves accessible presentation and refresh controls to the theme

### Requirement: Anonymous users can submit link applications
The system SHALL allow visitors to submit link applications through the native Form or REST
transport only while both the application master switch and visitor-submission switch are enabled.
Native Form requests SHALL pass CSRF, Cookie CAPTCHA, and shared submission-rate checks; REST
requests SHALL pass explicit CAPTCHA and the same submission-rate checks.

#### Scenario: Successful native Form submission
- **WHEN** application and visitor submission are enabled
- **AND** a visitor submits a form with `url`, `displayName`, `_csrf`, and a valid `captchaCode` to
  `/links/apply/submit`
- **THEN** the system creates a `LinkApplication` with status `PENDING` and origin type `FORM`
- **AND** redirects the browser to `/links?applied=success`

#### Scenario: Successful REST submission
- **WHEN** application and visitor submission are enabled
- **AND** a visitor submits a valid JSON application with a valid explicit REST CAPTCHA
- **THEN** the system creates a `LinkApplication` with status `PENDING` and origin type `FORM`
- **AND** returns the REST created result

#### Scenario: Authenticated visitor uses the public Form
- **WHEN** an authenticated visitor submits through `/links/apply/submit`
- **THEN** the request requires the same valid CAPTCHA as an anonymous submission

#### Scenario: Authenticated visitor uses the public REST resource
- **WHEN** an authenticated visitor submits through the REST application resource
- **THEN** the request requires the same valid explicit CAPTCHA as an anonymous submission
- **AND** authentication does not bypass visitor admission

#### Scenario: Submission with optional fields
- **WHEN** application and visitor submission are enabled
- **AND** a CAPTCHA-validated visitor submits `url`, `displayName`, `logo`, `description`, `email`,
  `backlink`, and `feedUrls`, where every provided URL uses HTTP or HTTPS
- **THEN** the system stores all provided fields in the `LinkApplication`
- **AND** reports success through the selected transport

#### Scenario: Native Form is missing a valid CSRF token
- **WHEN** a visitor submits a correct Cookie CAPTCHA without a valid CSRF token
- **THEN** Halo's CSRF protection rejects the request
- **AND** the system does not create a LinkApplication

#### Scenario: REST request has no CSRF token
- **WHEN** a visitor submits an otherwise valid REST request without a CSRF token
- **THEN** Halo's `/apis/**` security policy does not require one
- **AND** the request remains subject to explicit CAPTCHA and shared visitor admission

#### Scenario: Missing or invalid CAPTCHA
- **WHEN** a visitor submits a missing, malformed, incorrect, expired, or replayed CAPTCHA through
  either transport
- **THEN** the system does not call the shared LinkApplication creation service
- **AND** does not consume the formal application submission allowance
- **AND** reports the transport-specific CAPTCHA failure

#### Scenario: Native Form CAPTCHA failure protects submitted values
- **WHEN** native Form CAPTCHA validation fails
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

#### Scenario: Rejected Form application blocks resubmission
- **WHEN** a CAPTCHA-validated visitor submits a `url` whose canonical comparison key matches a
  `REJECTED` Form-origin LinkApplication
- **THEN** the system rejects the submission through either visitor transport
- **AND** native Form and REST remain equivalent because both record origin `FORM`

#### Scenario: Rejected Comment application allows visitor submission
- **WHEN** a CAPTCHA-validated visitor submits a `url` whose canonical comparison key matches only a
  `REJECTED` Comment-origin LinkApplication
- **THEN** the system allows the new visitor application when all other validation succeeds

#### Scenario: Rate limit is exceeded across transports
- **WHEN** a visitor submits a second CAPTCHA-validated request from the same IP within one minute
  through either transport
- **THEN** the system rejects the submission
- **AND** reports the transport-specific rate-limit result

#### Scenario: Concurrent rate-limit checks use the same IP
- **WHEN** two requests with independently valid CAPTCHAs from the same IP reach the shared
  submission limiter concurrently
- **THEN** the limiter atomically allows at most one request in the one-minute window
- **AND** expired IP entries are eligible for bounded cleanup

#### Scenario: Native Form contains an invalid URL
- **WHEN** a CAPTCHA-validated visitor submits a native Form with an invalid URL format
- **THEN** the system rejects the submission
- **AND** redirects the browser to
  `/links?applied=error&field=url&value=<submitted-value>&message=URL格式错误`

#### Scenario: Submission contains an invalid optional URL
- **WHEN** a CAPTCHA-validated visitor submits a non-HTTP-or-HTTPS `logo`, `backlink`, or `feedUrls`
  value through either transport
- **THEN** the system rejects the submission
- **AND** reports the invalid optional field according to the transport contract

#### Scenario: Submission is missing required fields
- **WHEN** a CAPTCHA-validated visitor submits without `url` or `displayName`
- **THEN** the system rejects the submission
- **AND** reports the validation failure according to the transport contract

#### Scenario: Native Form repopulates a non-CAPTCHA validation error
- **WHEN** a CAPTCHA-validated native Form submission fails with a field validation error
- **THEN** the redirect URL includes `applied=error`, the failing `field`, the submitted `value`, and
  an error `message`
- **AND** the theme template can use these query parameters to repopulate the Form and show error
  messages

### Requirement: Visitor submissions report pending capacity outcomes
The system SHALL preserve shared visitor security checks and duplicate behavior before reporting
pending-capacity outcomes through the native Form or REST transport.

#### Scenario: Native Form submission reaches full capacity
- **WHEN** a native Form visitor passes CAPTCHA and the IP submission limit with otherwise valid,
  non-duplicate input
- **AND** pending capacity is exhausted
- **THEN** the system does not persist a LinkApplication
- **AND** redirects to
  `/links?applied=error&message=待审核申请数量已达上限，请稍后再试`
- **AND** the redirect does not include `field`, `value`, or the configured capacity

#### Scenario: REST submission reaches full capacity
- **WHEN** a REST visitor passes CAPTCHA and the shared IP submission limit with otherwise valid,
  non-duplicate input
- **AND** pending capacity is exhausted
- **THEN** the system does not persist a LinkApplication
- **AND** returns the REST capacity Problem Detail
- **AND** does not expose the configured capacity

#### Scenario: Duplicate submission reaches full capacity
- **WHEN** a visitor submission is both a duplicate under existing source-aware rules and received
  while pending capacity is exhausted
- **THEN** the system returns the transport's duplicate result instead of the capacity-reached
  result

#### Scenario: Full-capacity submission consumes abuse controls
- **WHEN** a valid CAPTCHA and shared IP submission allowance reach authoritative creation while
  capacity is full
- **THEN** the CAPTCHA remains consumed
- **AND** the IP submission allowance remains consumed

#### Scenario: Capacity is full while the theme renders
- **WHEN** the application master and visitor-submission switches are enabled while capacity is full
- **THEN** the template model continues to expose `linkApplicationEnabled` as true
- **AND** both CAPTCHA operations remain available according to their shared security limits

#### Scenario: Native Form capacity evaluation fails
- **WHEN** authoritative capacity evaluation fails after native Form abuse controls pass
- **THEN** the system does not persist a LinkApplication
- **AND** redirects to `/links?applied=error&message=暂时无法提交，请稍后再试`
- **AND** the redirect does not include `field` or `value`

#### Scenario: REST capacity evaluation fails
- **WHEN** authoritative capacity evaluation fails after REST abuse controls pass
- **THEN** the system does not persist a LinkApplication
- **AND** returns the REST unavailable Problem Detail

#### Scenario: Full capacity is handled normally
- **WHEN** visitor creation returns the capacity-reached result
- **THEN** the system does not emit a warning or error log for that normal result

#### Scenario: Capacity evaluation fails operationally
- **WHEN** visitor capacity evaluation fails because settings or storage are unavailable
- **THEN** the system records an operational diagnostic
- **AND** the diagnostic excludes application fields and other attacker-controlled values

### Requirement: Visitor CAPTCHA challenges are short-lived and single-use
The system SHALL associate each native Form or REST image with one bounded process-local challenge
that can be consumed by at most one verification attempt.

#### Scenario: Native Form challenge cookie is issued
- **WHEN** a native Form CAPTCHA image is generated successfully
- **THEN** the response writes an opaque challenge identifier to a plugin-specific cookie
- **AND** the cookie has path `/links`, a five-minute maximum age, `HttpOnly`, and `SameSite=Lax`
- **AND** sets `Secure` when the effective request scheme is HTTPS
- **AND** does not bind the challenge to IP, authentication, session, or User-Agent

#### Scenario: REST challenge identifier is issued
- **WHEN** a REST CAPTCHA is generated successfully
- **THEN** its opaque challenge identifier is returned explicitly in the JSON response
- **AND** no challenge cookie is written
- **AND** the challenge has the same five-minute lifetime and no IP, authentication, session, or
  User-Agent binding

#### Scenario: A new native Form image replaces the browser challenge
- **WHEN** the same cookie jar successfully requests another native Form image
- **THEN** the store invalidates the challenge identified by the previous cookie
- **AND** the new challenge identifier overwrites that cookie
- **AND** only the newest cookie-associated challenge can be submitted from that cookie jar

#### Scenario: A new REST image preserves earlier REST challenges
- **WHEN** a REST client successfully requests another challenge
- **THEN** the new challenge does not invalidate earlier REST challenges
- **AND** all outstanding challenges remain independently bounded by expiry and store capacity

#### Scenario: Challenge expires
- **WHEN** five minutes have elapsed since native Form or REST challenge issuance
- **THEN** verification treats the challenge as invalid
- **AND** no application is created

#### Scenario: Verification consumes the challenge
- **WHEN** a request attempts CAPTCHA verification with a cookie or explicit challenge identifier
- **THEN** the store atomically removes the challenge before comparing the answer
- **AND** expires the challenge cookie when the native Form cookie transport was used
- **AND** the same challenge cannot be attempted again regardless of the result

#### Scenario: Concurrent verification replays one challenge
- **WHEN** multiple requests concurrently submit the same challenge and correct answer through one
  or both transports
- **THEN** at most one request passes CAPTCHA verification
- **AND** all other requests receive their transport's generic CAPTCHA error

#### Scenario: Challenge state is cleaned
- **WHEN** generation or verification accesses the challenge store
- **THEN** expired entries are eligible for lazy removal
- **AND** no periodic cleanup task is required

#### Scenario: Plugin stops
- **WHEN** the plugin instance stops
- **THEN** all outstanding native Form and REST challenge state is discarded

### Requirement: CAPTCHA generation is resource-bounded
The system SHALL share bounded image issuance, outstanding state, and drawing concurrency across
native Form and REST generation independently from the formal application submission limiter.

#### Scenario: Per-IP generation limit is exceeded
- **WHEN** one remote IP requests more than 10 CAPTCHA images within one minute across both
  transports
- **THEN** the selected CAPTCHA endpoint returns `429 Too Many Requests`
- **AND** the native Form image response provides `Retry-After`
- **AND** the REST Problem Detail provides `retryAfterSeconds` without requiring a `Retry-After`
  header
- **AND** the system does not draw an image or create a challenge

#### Scenario: Generation limiter state is bounded
- **WHEN** generation requests arrive from distinct remote IPs through either transport
- **THEN** the shared limiter tracks at most 10,000 IP entries
- **AND** expired entries are eligible for bounded cleanup
- **AND** the oldest remaining tracking entry is eligible for removal when a new IP arrives at
  capacity
- **AND** the plugin does not parse or trust forwarding headers itself

#### Scenario: Challenge capacity is available
- **WHEN** fewer than 10,000 unexpired native Form and REST challenges exist
- **THEN** an otherwise admitted request may create one challenge
- **AND** stores only its normalized answer and expiry, not image bytes

#### Scenario: Challenge capacity is exhausted
- **WHEN** lazy cleanup completes and 10,000 unexpired challenges remain
- **THEN** the selected CAPTCHA endpoint returns `503 Service Unavailable`
- **AND** does not evict an unexpired challenge

#### Scenario: Drawing concurrency is available
- **WHEN** fewer than four image renderings are active across both transports
- **THEN** an admitted request may render off the reactive event loop

#### Scenario: Drawing concurrency is exhausted
- **WHEN** four image renderings are active
- **THEN** another generation request immediately returns `503 Service Unavailable`
- **AND** does not enter an unbounded drawing queue

#### Scenario: Image generation fails
- **WHEN** font loading, drawing, encoding, or challenge storage fails
- **THEN** the selected CAPTCHA endpoint returns `503 Service Unavailable`
- **AND** native Form and REST verification remain fail closed

#### Scenario: Image generation does not consume submission allowance
- **WHEN** a visitor loads or refreshes CAPTCHA images through either transport
- **THEN** those requests do not consume the shared one-per-IP-per-minute application submission
  allowance

## REMOVED Requirements

### Requirement: Visitor submission responses are content negotiated
**Reason**: Native Form response negotiation is replaced by a dedicated JSON REST transport so the
Form endpoint has one request and redirect contract.

**Migration**: Clients that sent `Accept: application/json` to `/links/apply/submit` must issue a
REST CAPTCHA and submit JSON to `/apis/api.link.halo.run/v1alpha1/link-applications`.

### Requirement: Visitor JSON results use a stable envelope
**Reason**: The Form-specific `{status, code, field, message}` envelope is replaced by the REST
created-result and Halo Problem Details contracts.

**Migration**: Clients must handle the REST `201` body and branch on Problem Details `status` and
`type`.

### Requirement: Visitor JSON results use outcome-specific HTTP semantics
**Reason**: Outcome-specific HTTP semantics now belong exclusively to the REST capability; the
native Form transport always uses redirects for plugin-owned business outcomes.

**Migration**: Asynchronous clients must move to the REST application and CAPTCHA operations.
