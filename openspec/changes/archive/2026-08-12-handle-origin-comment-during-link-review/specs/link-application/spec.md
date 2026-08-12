## ADDED Requirements

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
