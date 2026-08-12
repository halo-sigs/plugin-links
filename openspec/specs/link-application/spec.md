# link-application Specification

## Purpose
Define how visitor and Comment-origin friend-link applications are created, deduplicated, reviewed,
approved into formal Links, rejected, inspected, and deleted.
## Requirements
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

### Requirement: Administrators can configure pending application capacity
The system SHALL expose `application.security.pendingCapacity` as a required positive integer under
a Security subgroup of friend-link application settings and SHALL use `100` as both the settings
schema and backend default.

#### Scenario: Administrator opens enabled application settings
- **WHEN** the friend-link application master switch is enabled
- **THEN** the settings form displays a Security subgroup
- **AND** displays a pending application capacity field with default value `100`
- **AND** explains that new applications pause at the limit until the pending count decreases

#### Scenario: Administrator configures a positive capacity
- **WHEN** the administrator saves a positive integer capacity
- **THEN** the system uses that value for subsequent supported application creation

#### Scenario: Administrator enters a non-positive capacity
- **WHEN** the administrator enters `0` or a negative capacity
- **THEN** settings validation rejects the value
- **AND** `0` is not interpreted as unlimited or disabled

#### Scenario: Capacity has no configured maximum
- **WHEN** the administrator configures a positive integer above the default
- **THEN** the settings schema does not reject it solely for exceeding an artificial maximum

#### Scenario: Application settings have not been persisted
- **WHEN** the application feature uses its initial unsaved settings
- **THEN** the backend pending application capacity is `100`

#### Scenario: Saved capacity is malformed
- **WHEN** the saved pending application capacity is explicitly malformed or non-positive
- **THEN** the system treats new application creation as unavailable
- **AND** does not fall back to an unlimited capacity

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

### Requirement: Pending capacity governs supported application creation
The system SHALL admit a new LinkApplication through supported plugin creation paths only when the
current number of `PENDING` LinkApplications is strictly less than the effective pending capacity.

#### Scenario: Visitor and Comment applications share capacity
- **WHEN** FORM-origin and COMMENT-origin LinkApplications are pending
- **THEN** both origins contribute to the same pending capacity

#### Scenario: Only pending applications consume capacity
- **WHEN** the system evaluates capacity
- **THEN** it counts LinkApplications with status `PENDING`
- **AND** does not count `APPROVING`, `APPROVED`, or `REJECTED` applications

#### Scenario: Capacity has one remaining slot
- **WHEN** the pending count is one less than the configured capacity
- **THEN** one otherwise valid supported creation may persist a new `PENDING` LinkApplication

#### Scenario: Capacity is exhausted
- **WHEN** the pending count is greater than or equal to the configured capacity
- **THEN** supported creation returns a distinct capacity-reached result
- **AND** does not persist a LinkApplication
- **AND** does not publish a new-application notification

#### Scenario: Capacity is lowered below the current count
- **WHEN** an administrator lowers capacity below the number of existing pending applications
- **THEN** the system preserves every existing application and its current status
- **AND** rejects supported new creation until the pending count becomes lower than the new capacity

#### Scenario: Pending application leaves the queue
- **WHEN** a pending application becomes `APPROVING` or `REJECTED`, or is deleted
- **THEN** it no longer consumes pending capacity

#### Scenario: Authoritative capacity cannot be evaluated
- **WHEN** effective capacity settings or the authoritative pending application query are unavailable
- **THEN** supported creation fails closed
- **AND** does not persist a LinkApplication

#### Scenario: Privileged caller writes directly through the Extension API
- **WHEN** a caller bypasses the plugin's shared creation service and writes a LinkApplication
  directly through Halo's Extension API
- **THEN** this capability does not guarantee enforcement for that write

### Requirement: Single-instance pending capacity is concurrency-safe
The system SHALL serialize authoritative capacity evaluation and persistence across all supported
LinkApplication creation attempts within one plugin instance.

#### Scenario: Different URLs compete for one remaining slot
- **WHEN** two otherwise valid requests with different canonical URLs concurrently observe one
  remaining pending slot
- **THEN** at most one request persists a new `PENDING` LinkApplication
- **AND** the other request receives the capacity-reached result

#### Scenario: Capacity coordination finishes
- **WHEN** an authoritative creation operation succeeds, is rejected, or fails
- **THEN** the process-wide creation gate is released
- **AND** later creation attempts can evaluate current capacity

#### Scenario: Another plugin instance creates an application
- **WHEN** supported creation executes in more than one plugin instance
- **THEN** this capability does not guarantee a distributed hard upper bound

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
- **AND** Console displays the verification result (success/failure) in a Toast while the detail
  view remains open

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
- **WHEN** an admitted request reaches `GET /links/apply/captcha` while visitor submission is enabled
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



### Requirement: Obsolete visitor application action paths are removed
The system SHALL expose visitor application submission and CAPTCHA generation only at their new
action paths and SHALL NOT retain the previous paths as aliases or redirects.

#### Scenario: Previous submission path is not exposed
- **WHEN** a client sends `POST /links/apply`
- **THEN** the plugin does not route the request to the visitor application submission handler

#### Scenario: Previous CAPTCHA path is not exposed
- **WHEN** a client sends `GET /links/captcha`
- **THEN** the plugin does not route the request to the visitor CAPTCHA handler

### Requirement: Source Comment state is available to application review
The system SHALL expose the current approval and hidden state of a Comment-origin application's
source Comment through that application's origin-Comment operation without allowing the caller to
select an arbitrary Comment.

#### Scenario: Current source state is returned
- **WHEN** a caller opens a Comment-origin application whose source Comment exists
- **THEN** the origin-Comment response includes the Comment's current `approved` and `hidden` values
- **AND** the values are read from the current Comment rather than copied into LinkApplication

#### Scenario: Source state changes after recognition
- **WHEN** the source Comment is approved, unapproved, hidden, or unhidden after application creation
- **THEN** the next origin-Comment response reflects the updated state

#### Scenario: Source Comment is unavailable
- **WHEN** the Comment recorded by the application has been deleted
- **THEN** the origin-Comment operation returns not found
- **AND** the application remains reviewable and approvable

### Requirement: Administrators can handle the source Comment during link approval
For Comment-origin applications, the Console SHALL allow an administrator with both link-management
and Comment-management permission to optionally approve the source Comment or create a reply after
link approval succeeds.

#### Scenario: Unapproved Comment defaults to approval
- **WHEN** an authorized administrator opens a pending Comment-origin application whose source
  Comment is unapproved
- **THEN** Console selects the option to approve the source Comment by default
- **AND** displays the selected link and Comment actions before submission

#### Scenario: Link approval and Comment approval succeed
- **WHEN** the administrator approves the link with source-Comment approval selected and no reply
- **THEN** the system first completes link approval
- **AND** then sets the source Comment to approved with an approval time
- **AND** reports both operations as successful

#### Scenario: Link approval and reply succeed
- **WHEN** the administrator approves the link with a non-blank plain-text reply
- **THEN** the system first completes link approval
- **AND** safely converts the plain text to reply content and creates the reply as the current user
- **AND** Halo's reply flow approves the source Comment and applies normal reply notifications

#### Scenario: Comment handling is not selected
- **WHEN** the administrator approves a Comment-origin application without selecting Comment
  approval and without entering a reply
- **THEN** the LinkApplication is approved without modifying the source Comment

#### Scenario: Application does not originate from a Comment
- **WHEN** an administrator reviews a form-origin application
- **THEN** Console does not display source-Comment actions

#### Scenario: Application is rejected
- **WHEN** an administrator views or rejects a Comment-origin application in `REJECTED` state
- **THEN** Console does not offer source-Comment mutation actions
- **AND** rejection does not approve, hide, unhide, or reply to the Comment

### Requirement: Comment handling preserves independent Comment state
The system SHALL derive Comment controls from current Comment state and SHALL NOT treat link
approval as authority to unhide or otherwise reset Comment moderation.

#### Scenario: Source Comment is already approved
- **WHEN** an authorized administrator opens a Comment-origin application whose source Comment is
  already approved
- **THEN** Console does not offer or issue a redundant approval mutation
- **AND** still allows the administrator to create an optional reply

#### Scenario: Source Comment is hidden
- **WHEN** the source Comment is hidden
- **THEN** Console warns that the Comment will remain hidden
- **AND** Comment approval or reply creation does not change the hidden state

#### Scenario: Approved application is opened again
- **WHEN** an authorized administrator opens an `APPROVED` Comment-origin application whose source
  Comment still exists
- **THEN** Console allows any still-applicable Comment approval and reply actions through a separate
  Comment-processing action

#### Scenario: Source Comment is deleted before submission
- **WHEN** the source Comment is unavailable while the application is being reviewed
- **THEN** Console disables Comment actions and explains that the source was deleted
- **AND** the administrator can still approve the link application

### Requirement: Link and Comment outcomes remain truthful under failure
The system SHALL treat link approval and source-Comment handling as ordered independent operations,
preserve successful link approval, and prevent automatic duplicate replies.

#### Scenario: Link approval fails
- **WHEN** link approval fails validation or does not complete
- **THEN** the system does not submit any selected source-Comment mutation

#### Scenario: Comment handling fails after link approval
- **WHEN** link approval succeeds and the selected Comment operation returns a determinate failure
- **THEN** the Link and `APPROVED` LinkApplication remain persisted
- **AND** Console keeps the detail view open in approved mode
- **AND** reports that link approval succeeded while Comment handling failed
- **AND** allows the administrator to retry only the still-applicable Comment action

#### Scenario: Reply result is indeterminate
- **WHEN** a timeout or network interruption prevents Console from knowing whether reply creation
  completed
- **THEN** Console does not automatically retry or claim a definite failure
- **AND** refreshes current Comment and reply state
- **AND** requires administrator confirmation before a manual resubmission

#### Scenario: Comment approval becomes complete concurrently
- **WHEN** refreshed source state shows that another operation already approved the Comment
- **THEN** Console treats Comment approval as complete without submitting another approval patch

### Requirement: Comment reply input remains safe and focused
The Console SHALL accept a plain-text source-Comment reply and SHALL convert it to safe reply content
without depending on Halo Console's internal rich-text editor components.

#### Scenario: Plain-text reply contains markup characters
- **WHEN** an administrator enters reply text containing HTML-significant characters
- **THEN** Console preserves the text as raw reply content
- **AND** escapes those characters in rendered reply content rather than interpreting them as HTML

#### Scenario: Reply is blank
- **WHEN** the reply input contains only whitespace
- **THEN** Console does not submit a reply request
- **AND** may still approve the Comment when Comment approval is selected
