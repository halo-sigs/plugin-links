# link-application Specification

## Purpose
TBD - created by archiving change add-link-application. Update Purpose after archive.
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

### Requirement: Administrators can inspect application source context
The system SHALL expose application-origin context in the existing pending application review flow.

#### Scenario: Pending list shows source
- **WHEN** the pending application list contains form, comment, or historical applications
- **THEN** each item displays the corresponding source label

#### Scenario: Comment application detail is opened
- **WHEN** an administrator opens a comment-origin application
- **THEN** the Console loads the referenced Comment
- **AND** displays its current subject, comment-management link, and raw content when available

#### Scenario: Original comment is unavailable
- **WHEN** a comment-origin application references a Comment that has been deleted or is otherwise unavailable
- **THEN** the application remains reviewable
- **AND** the Console indicates that the original Comment is unavailable

### Requirement: Anonymous users can submit link applications
The system SHALL allow anonymous visitors to submit link applications via an HTML form POST to `/links/apply`.

#### Scenario: Successful submission
- **WHEN** an anonymous user submits a form with `url` and `displayName` to `/links/apply`
- **THEN** the system creates a `LinkApplication` with status `PENDING` and origin type `FORM`
- **AND** redirects the browser to `/links?applied=success`

#### Scenario: Submission with optional fields
- **WHEN** an anonymous user submits a form with `url`, `displayName`, `logo`, `description`, `email`, `backlink`, and `feedUrls`
- **THEN** the system stores all provided fields in the `LinkApplication`
- **AND** redirects the browser to `/links?applied=success`

#### Scenario: Existing formal Link rejects submission
- **WHEN** an anonymous user submits a `url` whose canonical comparison key matches an existing formal Link
- **THEN** the system rejects the submission as a duplicate

#### Scenario: Active application rejects submission
- **WHEN** an anonymous user submits a `url` whose canonical comparison key matches a `PENDING` or `APPROVED` LinkApplication from any source
- **THEN** the system rejects the submission as a duplicate

#### Scenario: Rejected form application blocks resubmission
- **WHEN** an anonymous user submits a `url` whose canonical comparison key matches a `REJECTED` form-origin LinkApplication
- **THEN** the system rejects the submission
- **AND** redirects the browser to `/links?applied=error&field=url&message=该链接已提交申请`

#### Scenario: Rejected comment application allows form submission
- **WHEN** an anonymous user submits a `url` whose canonical comparison key matches only a `REJECTED` comment-origin LinkApplication
- **THEN** the system allows the new form application when all other validation succeeds

#### Scenario: Rate limit exceeded
- **WHEN** an anonymous user submits a second request from the same IP within 1 minute
- **THEN** the system rejects the submission
- **AND** redirects the browser to `/links?applied=error&message=提交过于频繁，请稍后再试`

#### Scenario: Invalid URL format
- **WHEN** an anonymous user submits a form with an invalid URL format
- **THEN** the system rejects the submission
- **AND** redirects the browser to `/links?applied=error&field=url&value=<submitted-value>&message=URL格式错误`

#### Scenario: Missing required fields
- **WHEN** an anonymous user submits a form without `url` or `displayName`
- **THEN** the system rejects the submission
- **AND** redirects the browser to `/links?applied=error&field=<missing-field>&message=必填字段不能为空`

#### Scenario: Form value回填 on error
- **WHEN** a submission fails with validation errors
- **THEN** the redirect URL includes `applied=error`, the failing `field`, the submitted `value`, and an error `message`
- **AND** the theme template can use these query parameters to repopulate the form and show error messages

### Requirement: Administrators can view pending applications
The system SHALL provide a Console UI for administrators to view pending link applications and
their source.

#### Scenario: Pending count alert
- **WHEN** an administrator opens the Link management page
- **THEN** a card at the top of the page displays the count of `PENDING` applications
- **AND** clicking the card opens a modal listing all `PENDING` applications

#### Scenario: Application list display
- **WHEN** the application list modal is open
- **THEN** each `PENDING` application is displayed with its `url`, `displayName`, submission time, and source label
- **AND** clicking an application opens its detail view

#### Scenario: Recognition is configured but unavailable
- **WHEN** comment application recognition settings are enabled but the AI integration is not operational
- **THEN** the Link management page displays a non-blocking warning
- **AND** application review and non-AI link management remain available

### Requirement: Administrators can approve link applications
The system SHALL allow administrators to approve a pending application, creating a formal `Link`.

#### Scenario: Approve with modifications
- **WHEN** an administrator opens an application detail view
- **THEN** all fields (`url`, `displayName`, `logo`, `description`) are editable
- **AND** a dropdown allows selecting a `LinkGroup` to assign
- **AND** clicking "Approve" creates a new `Link` with the (potentially modified) field values and selected group
- **AND** the `LinkApplication` status is updated to `APPROVED`

#### Scenario: Post-approval automation
- **WHEN** an application is approved
- **THEN** the system automatically triggers link detail fetching
- **AND** the system automatically triggers RSS feed refresh for the new `Link`

#### Scenario: Approve without group assignment
- **WHEN** an administrator approves an application without selecting a group
- **THEN** the created `Link` has no group assignment (ungrouped)

### Requirement: Administrators can reject link applications
The system SHALL allow administrators to reject a pending application and apply source-aware future
submission rules.

#### Scenario: Reject application
- **WHEN** an administrator clicks "Reject" on a pending application
- **THEN** the `LinkApplication` status is updated to `REJECTED`
- **AND** no `Link` is created

#### Scenario: Rejected form URL blocks resubmission
- **WHEN** a user attempts to submit or automatically recognize a URL that matches a `REJECTED` form-origin application
- **THEN** the new application is not created

#### Scenario: Rejected comment URL permits later form submission
- **WHEN** a user submits a form URL that matches only a `REJECTED` comment-origin application
- **THEN** the form submission is not blocked by that rejected comment application

### Requirement: Administrators can manually verify backlinks
The system SHALL allow administrators to manually trigger backlink verification during the approval process.

#### Scenario: Manual verification trigger
- **WHEN** an administrator clicks "Verify Backlink" in the application detail view
- **THEN** the system fetches the submitted `backlink` URL
- **AND** checks whether the page contains a link to the site's own URL
- **AND** displays the verification result (success/failure) in the detail view

### Requirement: Administrators can delete link applications
The system SHALL allow administrators to delete `LinkApplication` records in any status.

#### Scenario: Delete approved application
- **WHEN** an administrator deletes an `APPROVED` application
- **THEN** the `LinkApplication` record is permanently removed
- **AND** the associated `Link` is NOT affected

#### Scenario: Delete pending application
- **WHEN** an administrator deletes a `PENDING` application
- **THEN** the `LinkApplication` record is permanently removed

### Requirement: LinkApplication lifecycle management
The system SHALL manage `LinkApplication` records through their lifecycle states.

#### Scenario: Pending to approved transition
- **WHEN** a `PENDING` application is approved
- **THEN** its status becomes `APPROVED`
- **AND** the record is retained (not deleted)

#### Scenario: Pending to rejected transition
- **WHEN** a `PENDING` application is rejected
- **THEN** its status becomes `REJECTED`
- **AND** the record is retained (not deleted)
