# link-application Specification

## Purpose
Define how visitor and Comment-origin friend-link applications are created, deduplicated, reviewed,
approved into formal Links, rejected, inspected, and deleted.
## Requirements

### Requirement: Link applications record their origin
The system SHALL record origin information for newly created LinkApplication resources while
remaining compatible with historical records that have no origin.

#### Scenario: Form application records origin
- **WHEN** an anonymous visitor successfully submits `/links/apply`
- **THEN** the created LinkApplication has origin type `FORM`

#### Scenario: Comment application records recognition origin
- **WHEN** comment recognition creates a LinkApplication
- **THEN** the application has origin type `COMMENT`
- **AND** records its source Comment metadata name as `origin.comment.name`

#### Scenario: Historical application has no origin
- **WHEN** an administrator views a LinkApplication created before origin support
- **THEN** the application remains readable and actionable
- **AND** the Console labels its source as historical

### Requirement: Link application settings govern new application creation
The system SHALL provide a disabled-by-default Link Application settings group whose master switch
controls all new LinkApplication creation channels.

#### Scenario: Application feature is disabled by default
- **WHEN** the plugin is installed or upgraded without Link Application settings
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
The system SHALL document and expose the complete same-origin HTML form contract required by Halo
themes.

#### Scenario: Theme evaluates form availability
- **WHEN** the links template is rendered
- **THEN** the model exposes `linkApplicationEnabled`
- **AND** its value is true only when the master and visitor-submission switches are enabled

#### Scenario: Theme renders a CSRF-protected form
- **WHEN** a theme renders the application form
- **THEN** the documented example includes the CSRF hidden field and supported application fields
- **AND** submits `application/x-www-form-urlencoded` to `/links/apply`

#### Scenario: Theme submits with JavaScript
- **WHEN** a theme implements the form with same-origin JavaScript
- **THEN** the documentation provides a form-encoded example using the same CSRF and redirect
  contract
- **AND** does not require a JSON application API

#### Scenario: Theme handles result redirects
- **WHEN** submission succeeds, fails validation, is rate limited, is duplicated, or is disabled
- **THEN** the documentation defines the corresponding `applied`, `field`, `value`, and `message`
  query parameters

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
without granting link managers permission to read arbitrary Comments.

#### Scenario: Pending list shows source
- **WHEN** the application list contains form, Comment, or historical applications
- **THEN** each item displays the corresponding source label

#### Scenario: Comment application detail is opened
- **WHEN** an administrator with link-application management permission opens a Comment-origin
  application
- **THEN** Console requests the source through that LinkApplication's origin-Comment operation
- **AND** displays its current subject, Comment-management link, raw content, and creation time when
  available

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
The system SHALL allow anonymous visitors to submit link applications via an HTML form POST to
`/links/apply` only while both the application master switch and visitor-submission switch are
enabled.

#### Scenario: Successful submission
- **WHEN** application and visitor submission are enabled
- **AND** an anonymous user submits a form with `url` and `displayName` to `/links/apply`
- **THEN** the system creates a `LinkApplication` with status `PENDING` and origin type `FORM`
- **AND** redirects the browser to `/links?applied=success`

#### Scenario: Submission with optional fields
- **WHEN** application and visitor submission are enabled
- **AND** an anonymous user submits a form with `url`, `displayName`, `logo`, `description`, `email`,
  `backlink`, and `feedUrls`
- **THEN** the system stores all provided fields in the `LinkApplication`
- **AND** redirects the browser to `/links?applied=success`

#### Scenario: Existing formal Link rejects submission
- **WHEN** an anonymous user submits a `url` whose canonical comparison key matches an existing
  formal Link
- **THEN** the system rejects the submission as a duplicate

#### Scenario: Active application rejects submission
- **WHEN** an anonymous user submits a `url` whose canonical comparison key matches a `PENDING`,
  `APPROVING`, or `APPROVED` LinkApplication from any source
- **THEN** the system rejects the submission as a duplicate

#### Scenario: Rejected form application blocks resubmission
- **WHEN** an anonymous user submits a `url` whose canonical comparison key matches a `REJECTED`
  form-origin LinkApplication
- **THEN** the system rejects the submission
- **AND** redirects the browser to
  `/links?applied=error&field=url&message=该链接已提交申请`

#### Scenario: Rejected comment application allows form submission
- **WHEN** an anonymous user submits a `url` whose canonical comparison key matches only a
  `REJECTED` Comment-origin LinkApplication
- **THEN** the system allows the new form application when all other validation succeeds

#### Scenario: Rate limit exceeded
- **WHEN** an anonymous user submits a second request from the same IP within 1 minute
- **THEN** the system rejects the submission
- **AND** redirects the browser to
  `/links?applied=error&message=提交过于频繁，请稍后再试`

#### Scenario: Concurrent rate-limit checks use the same IP
- **WHEN** two requests from the same IP reach the rate limiter concurrently
- **THEN** the limiter atomically allows at most one request in the one-minute window
- **AND** expired IP entries are eligible for bounded cleanup

#### Scenario: Invalid URL format
- **WHEN** an anonymous user submits a form with an invalid URL format
- **THEN** the system rejects the submission
- **AND** redirects the browser to
  `/links?applied=error&field=url&value=<submitted-value>&message=URL格式错误`

#### Scenario: Missing required fields
- **WHEN** an anonymous user submits a form without `url` or `displayName`
- **THEN** the system rejects the submission
- **AND** redirects the browser to
  `/links?applied=error&field=<missing-field>&message=必填字段不能为空`

#### Scenario: Form value回填 on error
- **WHEN** a submission fails with validation errors
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
- **THEN** all approval fields (`url`, `displayName`, `logo`, `description`) are editable
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
- **THEN** the system fetches the submitted `backlink` URL
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
