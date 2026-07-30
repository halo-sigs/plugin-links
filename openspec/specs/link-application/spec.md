# link-application Specification

## Purpose
Define how visitor and Comment-origin friend-link applications are created, deduplicated, reviewed,
approved into formal Links, rejected, inspected, and deleted.
## Requirements

### Requirement: Link applications record their origin
The system SHALL require origin information on every LinkApplication resource.

#### Scenario: Form application records origin
- **WHEN** an anonymous visitor successfully submits `/links/apply`
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

### Requirement: Single-instance application creation is concurrency-safe
The system SHALL serialize the complete duplicate-check and create operation by canonical URL within
one plugin instance for both form and Comment sources.

#### Scenario: Concurrent form submissions use the same canonical URL
- **WHEN** two form requests with the same canonical URL are processed concurrently
- **THEN** at most one request creates a PENDING LinkApplication
- **AND** the other request receives the existing duplicate response

#### Scenario: Form and Comment recognition race for the same URL
- **WHEN** a form submission and positive Comment recognition with the same canonical URL are
  processed concurrently in one plugin instance
- **THEN** at most one LinkApplication is created
- **AND** source-aware duplicate rules are applied after entering the serialized operation

#### Scenario: Serialized creation finishes
- **WHEN** a serialized create operation succeeds or fails
- **THEN** its process-local coordination entry is released
- **AND** completed URL keys do not accumulate without bound

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

### Requirement: Administrators can browse application history
The system SHALL provide a real paginated application history sorted by newest creation time and
filterable by status and origin type.

#### Scenario: Administrator opens application history
- **WHEN** an administrator opens the application list
- **THEN** Console requests the first 20 applications by default
- **AND** displays the server-reported page, size, and total

#### Scenario: Administrator filters application history
- **WHEN** an administrator filters by status or origin type
- **THEN** the backend applies all selected filters before pagination
- **AND** returns only matching applications

#### Scenario: Pending summary is displayed
- **WHEN** the Link management page loads
- **THEN** the pending summary uses the server-reported total for status `PENDING`

#### Scenario: Approval in progress is displayed
- **WHEN** history contains an `APPROVING` application
- **THEN** Console labels it as approval in progress
- **AND** exposes only the continue-approval operation

### Requirement: Administrators can inspect application source context
The system SHALL expose application-origin context through an application-scoped backend operation
without granting link managers permission to read arbitrary Comments. For Comment-origin
applications, the operation SHALL resolve an optional public-facing subject display through the
registered Halo `CommentSubject` extension point, and Console SHALL use only resolved display
values or generic fallback text for user-visible source information.

#### Scenario: Pending list shows source
- **WHEN** the application list contains form or Comment applications
- **THEN** each item displays the corresponding source label

#### Scenario: Comment application detail is opened
- **WHEN** an administrator with link-application management permission opens a Comment-origin
  application
- **THEN** Console requests the source through that LinkApplication's origin-Comment operation
- **AND** the backend resolves the Comment's current subject through the matching registered
  `CommentSubject`
- **AND** the response includes the original Comment data and an optional subject display with
  `title`, `url`, and `kindName`
- **AND** Console displays the resolved kind and title as a link to the public-facing subject URL
  that opens in a new browser tab
- **AND** Console displays the general Comment-management link, raw content, and creation time when
  available
- **AND** Console does not expose a subject editor route from the source display

#### Scenario: Subject display is unavailable
- **WHEN** the original Comment exists but no matching `CommentSubject` returns a subject display
- **THEN** the origin-Comment operation still returns the original Comment data
- **AND** the response has no resolved subject display
- **AND** Console displays that the source page is unavailable
- **AND** the application remains reviewable

#### Scenario: Subject display contains a non-public or unreachable URL
- **WHEN** a matching `CommentSubject` returns a display for a draft, private, recycled,
  unpublished, or otherwise unreachable subject
- **THEN** the origin-Comment operation returns the provider-supplied display without applying
  subject-type-specific publication filtering
- **AND** Console does not claim that the URL is currently reachable

#### Scenario: Console renders application source context
- **WHEN** Console displays resolved or unavailable Comment-origin source context
- **THEN** it MUST NOT render the `metadata.name` of the Comment, subject, LinkApplication, or any
  other referenced resource as user-visible text
- **AND** it MUST NOT fall back to `subjectRef.name` when a subject title is blank or unavailable
- **AND** blank resolved titles fall back to the resolved kind name and then generic source text

#### Scenario: Caller attempts to choose an arbitrary Comment
- **WHEN** a caller requests source context for a LinkApplication
- **THEN** the backend resolves only the Comment name recorded by that application
- **AND** the caller cannot supply a different Comment name

#### Scenario: Original comment is unavailable
- **WHEN** a Comment-origin application references a Comment that has been deleted
- **THEN** the source operation returns not found
- **AND** the application remains reviewable
- **AND** Console indicates that the original Comment is unavailable

#### Scenario: Caller lacks application-management permission
- **WHEN** a caller without link-application management permission requests source Comment context
- **THEN** the operation is forbidden
- **AND** Console does not mislabel the authorization failure as a deleted Comment

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

### Requirement: Administrators can view pending applications
The system SHALL provide a Console UI for administrators to view pending link applications, their
source, and recognition availability.

#### Scenario: Pending count alert
- **WHEN** an administrator opens the Link management page
- **THEN** a card at the top of the page displays the server-reported count of `PENDING`
  applications
- **AND** clicking the card opens the application list filtered to `PENDING`

#### Scenario: Application list display
- **WHEN** the application list is filtered to `PENDING`
- **THEN** each application is displayed with its `url`, `displayName`, submission time, and source
  label
- **AND** clicking an application opens its detail view

#### Scenario: Enabled recognition is unavailable
- **WHEN** the application master switch and Comment-recognition child switch are enabled
- **AND** the selected AI integration is not operational
- **THEN** the Link management page displays a non-blocking warning
- **AND** application review and non-AI link management remain available

#### Scenario: Recognition master switch is disabled
- **WHEN** the application master switch or Comment-recognition child switch is disabled
- **THEN** Console does not report recognition as operationally unavailable

### Requirement: Administrators can approve link applications
The system SHALL approve an application through a resumable, idempotent lifecycle that creates at
most one owned formal `Link` for that application.

#### Scenario: Approve with modifications
- **WHEN** an administrator opens a `PENDING` application detail view
- **THEN** all approval fields (`url`, `displayName`, `logo`, `description`, `backlink`, and
  `feedUrls`) are editable
- **AND** a dropdown allows selecting a `LinkGroup`
- **AND** the backend validates and normalizes the effective fields before reserving approval

#### Scenario: Invalid approval override
- **WHEN** approval contains an invalid URL, missing required field, unknown group, or URL that is
  already a formal Link or active application
- **THEN** the backend rejects approval before changing the application from `PENDING`
- **AND** does not create a Link

#### Scenario: Approval is reserved
- **WHEN** a valid approval request wins the resource-version update for a `PENDING` application
- **THEN** the application status becomes `APPROVING`
- **AND** the normalized approval fields and a stable Link name are stored under `spec.approval`
- **AND** later retries cannot replace the stored approval fields

#### Scenario: Concurrent administrators approve the same application
- **WHEN** two administrators approve the same `PENDING` application concurrently
- **THEN** only one request reserves the `APPROVING` transition
- **AND** both requests converge on the same stored approval and Link identity
- **AND** at most one Link is created

#### Scenario: Approval is resumed after interruption
- **WHEN** approval is requested for an `APPROVING` application
- **THEN** the backend resumes the stored approval request
- **AND** reuses an existing Link owned by the application when it was already created
- **AND** does not create a second Link

#### Scenario: Approval completes
- **WHEN** the owned Link exists for an `APPROVING` application
- **THEN** the application status becomes `APPROVED`
- **AND** `spec.approval.linkName` identifies the formal Link
- **AND** the Link contains the approved fields and optional group assignment

#### Scenario: Completed approval is retried
- **WHEN** approval is requested again for an `APPROVED` application with a recorded Link
- **THEN** the operation returns that Link without creating or modifying another Link

#### Scenario: Reject races with approval reservation
- **WHEN** approve and reject requests race for the same `PENDING` application
- **THEN** resource-version conflict handling allows only one lifecycle transition to win
- **AND** a `REJECTED` application never creates a Link

#### Scenario: Approval is interrupted after reservation
- **WHEN** infrastructure failure occurs after status becomes `APPROVING`
- **THEN** the application remains `APPROVING`
- **AND** Console allows an administrator to continue the same frozen approval safely

#### Scenario: Post-approval automation
- **WHEN** an application reaches `APPROVED`
- **THEN** the backend automatically triggers verification and initial RSS refresh for the new Link

#### Scenario: Post-approval automation fails
- **WHEN** verification or RSS refresh fails after approval
- **THEN** the formal Link and `APPROVED` application remain persisted
- **AND** the failure is exposed through the existing Link runtime status or retry operation

#### Scenario: Approve without group assignment
- **WHEN** an administrator approves an application without selecting a group
- **THEN** the created Link has no group assignment

### Requirement: Administrators can reject link applications
The system SHALL allow administrators to reject only pending applications and SHALL communicate the
source-aware future-submission behavior.

#### Scenario: Reject application
- **WHEN** an administrator rejects a `PENDING` application
- **THEN** the LinkApplication status is updated to `REJECTED`
- **AND** no Link is created

#### Scenario: Reject application already in approval
- **WHEN** an administrator attempts to reject an `APPROVING` or `APPROVED` application
- **THEN** the system rejects the lifecycle transition

#### Scenario: Rejected form URL blocks resubmission
- **WHEN** a user attempts to submit or automatically recognize a URL that matches a `REJECTED`
  form-origin application
- **THEN** the new application is not created
- **AND** Console explains that the URL remains blocked while the rejected record exists

#### Scenario: Rejected comment URL permits later form submission
- **WHEN** a user submits a form URL that matches only a `REJECTED` Comment-origin application
- **THEN** the form submission is not blocked by that rejected Comment application
- **AND** Console does not claim that the URL can never be submitted again

### Requirement: Administrators can manually verify backlinks
The system SHALL allow administrators to manually trigger backlink verification during the approval process.

#### Scenario: Manual verification trigger
- **WHEN** an administrator clicks "Verify Backlink" in the application detail view
- **THEN** the system fetches the current `backlink` input value
- **AND** checks whether the page contains a link to the site's own URL
- **AND** displays the verification result (success/failure) in the detail view

### Requirement: Administrators can delete link applications
The system SHALL allow administrators to delete individual applications and all deletable
applications matching the current server-side history filter.

#### Scenario: Delete approved application
- **WHEN** an administrator deletes an `APPROVED` application
- **THEN** the LinkApplication record is permanently removed
- **AND** the associated Link is not affected

#### Scenario: Delete pending application
- **WHEN** an administrator deletes a `PENDING` application
- **THEN** the LinkApplication record is permanently removed
- **AND** its URL can be submitted again when no other duplicate rule applies

#### Scenario: Delete rejected application
- **WHEN** an administrator deletes a `REJECTED` application
- **THEN** the LinkApplication record is permanently removed
- **AND** any duplicate-blocking effect of that record is removed

#### Scenario: Delete approving application
- **WHEN** an administrator attempts to delete an `APPROVING` application
- **THEN** the system rejects individual deletion
- **AND** preserves the application for safe approval recovery

#### Scenario: Clean current filtered result
- **WHEN** an administrator confirms cleanup for the current status and origin filters
- **THEN** the backend reapplies those filters and processes every match across all pages
- **AND** deletes matching `PENDING`, `APPROVED`, and `REJECTED` applications
- **AND** skips matching `APPROVING` applications
- **AND** returns matched, deleted, failed, and skipped counts

#### Scenario: Cleanup confirmation includes records that can block resubmission
- **WHEN** the current cleanup filter includes pending or rejected form-origin applications
- **THEN** Console warns that deleting them may allow those URLs to be submitted again

### Requirement: LinkApplication lifecycle management
The system SHALL manage LinkApplication records through `PENDING`, `APPROVING`, `APPROVED`, and
`REJECTED` lifecycle states.

#### Scenario: Pending to approving transition
- **WHEN** a valid approval request reserves a `PENDING` application
- **THEN** its status becomes `APPROVING`
- **AND** its approval request and stable Link name are persisted

#### Scenario: Approving to approved transition
- **WHEN** the formal Link for an `APPROVING` application is durably available
- **THEN** its status becomes `APPROVED`
- **AND** the record is retained

#### Scenario: Pending to rejected transition
- **WHEN** a `PENDING` application is rejected
- **THEN** its status becomes `REJECTED`
- **AND** the record is retained

#### Scenario: Approving application is recoverable
- **WHEN** an application remains `APPROVING` after an interrupted request
- **THEN** it can resume only the persisted approval operation
- **AND** cannot be rejected or deleted while in that state

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
