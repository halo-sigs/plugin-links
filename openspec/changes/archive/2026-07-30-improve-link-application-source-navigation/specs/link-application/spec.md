## MODIFIED Requirements

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
