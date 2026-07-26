## ADDED Requirements

### Requirement: Anonymous users can submit link applications
The system SHALL allow anonymous visitors to submit link applications via an HTML form POST to `/links/apply`.

#### Scenario: Successful submission
- **WHEN** an anonymous user submits a form with `url` and `displayName` to `/links/apply`
- **THEN** the system creates a `LinkApplication` with status `PENDING`
- **AND** redirects the browser to `/links?applied=success`

#### Scenario: Submission with optional fields
- **WHEN** an anonymous user submits a form with `url`, `displayName`, `logo`, `description`, `email`, `backlink`, and `feedUrls`
- **THEN** the system stores all provided fields in the `LinkApplication`
- **AND** redirects the browser to `/links?applied=success`

#### Scenario: Duplicate URL rejected
- **WHEN** an anonymous user submits a form with a `url` that already exists in a `PENDING` or `REJECTED` `LinkApplication`
- **THEN** the system rejects the submission
- **AND** redirects the browser to `/links?applied=error&field=url&message=该链接已提交申请`

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
The system SHALL provide a Console UI for administrators to view pending link applications.

#### Scenario: Pending count alert
- **WHEN** an administrator opens the Link management page
- **THEN** a card at the top of the page displays the count of `PENDING` applications
- **AND** clicking the card opens a modal listing all `PENDING` applications

#### Scenario: Application list display
- **WHEN** the application list modal is open
- **THEN** each `PENDING` application is displayed with its `url`, `displayName`, and submission time
- **AND** clicking an application opens its detail view

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
The system SHALL allow administrators to reject a pending application.

#### Scenario: Reject application
- **WHEN** an administrator clicks "Reject" on a pending application
- **THEN** the `LinkApplication` status is updated to `REJECTED`
- **AND** no `Link` is created

#### Scenario: Rejected URL blocks resubmission
- **WHEN** a user attempts to submit a `url` that exists in a `REJECTED` application
- **THEN** the submission is rejected as a duplicate

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
