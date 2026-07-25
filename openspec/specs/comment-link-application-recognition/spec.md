# comment-link-application-recognition Specification

## Purpose
Define configurable AI-powered recognition of new friend-link applications from selected Halo
comment sources, including safe model execution, traceable application creation, and optional AI
Foundation integration.

## Requirements

### Requirement: Administrators can configure comment application recognition
The system SHALL allow administrators to enable AI-powered friend-link application recognition for
an explicit set of comment subjects.

#### Scenario: Recognition is disabled by default
- **WHEN** the plugin is installed or upgraded without an existing recognition configuration
- **THEN** automatic comment application recognition is disabled

#### Scenario: Configure links page source
- **WHEN** an administrator adds a recognition source with type `LINKS`
- **THEN** new comments whose subject is the current PluginLinks links page are eligible for recognition
- **AND** no additional subject selection is required

#### Scenario: Configure post source
- **WHEN** an administrator adds a recognition source with type `POST`
- **THEN** the administrator must select exactly one post
- **AND** only new comments whose subject is that post are eligible for recognition

#### Scenario: Configure single-page source
- **WHEN** an administrator adds a recognition source with type `SINGLE_PAGE`
- **THEN** the administrator must select exactly one single page
- **AND** only new comments whose subject is that single page are eligible for recognition

#### Scenario: Duplicate sources are configured
- **WHEN** the same comment subject appears more than once in the configured source list
- **THEN** the system treats the entries as one source
- **AND** a new comment triggers at most one model analysis

#### Scenario: Required runtime configuration is incomplete
- **WHEN** the global AI switch, recognition switch, selected model, source list, or AI Foundation integration is unavailable
- **THEN** the system does not analyze new comments
- **AND** non-AI link management remains operational

### Requirement: Only newly created top-level comments are analyzed
The system SHALL analyze only new top-level `Comment` resources created while recognition is
operational and whose subject matches the configured sources.

#### Scenario: Matching comment is created
- **WHEN** a top-level Comment is created for a configured subject
- **THEN** the system queues the Comment for one recognition attempt

#### Scenario: Unapproved or hidden comment is created
- **WHEN** a matching top-level Comment is created as unapproved or hidden
- **THEN** the Comment remains eligible for recognition

#### Scenario: Comment from any owner type is created
- **WHEN** a matching top-level Comment is created by an email owner or Halo user
- **THEN** the Comment remains eligible for recognition

#### Scenario: Reply is created
- **WHEN** a Reply is created under a matching Comment
- **THEN** the system does not analyze the Reply

#### Scenario: Existing comment is updated or deleted
- **WHEN** an existing Comment is edited, approved, unapproved, hidden, or deleted
- **THEN** the system does not analyze the Comment again
- **AND** any existing LinkApplication remains unchanged

#### Scenario: Plugin starts with existing comments
- **WHEN** the recognition controller starts or restarts
- **THEN** the system does not scan or enqueue comments that already exist

#### Scenario: Comment is created while recognition is unavailable
- **WHEN** a Comment is created while recognition is disabled or AI Foundation is unavailable
- **THEN** the system does not analyze that Comment later when recognition becomes available

### Requirement: AI classifies and extracts comment application data
The system SHALL use the explicitly configured AI Foundation language model to classify a matching
comment and extract structured friend-link application fields.

#### Scenario: Minimal comment context is sent
- **WHEN** the system analyzes a matching Comment
- **THEN** it sends the raw comment, subject type and title, owner display name, and owner website when available
- **AND** it does not send the owner's email, IP address, user agent, or unrelated account identifiers

#### Scenario: Comment content contains instructions
- **WHEN** comment text attempts to instruct or redirect the model
- **THEN** the system treats the text as untrusted application data
- **AND** uses the plugin's fixed versioned system prompt

#### Scenario: Model rejects application classification
- **WHEN** structured output has `isLinkApplication` equal to `false`
- **THEN** the system does not create a LinkApplication

#### Scenario: Model accepts application with URL
- **WHEN** structured output has `isLinkApplication` equal to `true`
- **AND** the model returns a valid HTTP or HTTPS website URL
- **THEN** the system uses the model URL as the application URL

#### Scenario: Model accepts application without URL
- **WHEN** structured output has `isLinkApplication` equal to `true`
- **AND** the model does not return a valid URL
- **AND** the comment owner has a valid HTTP or HTTPS website
- **THEN** the system uses the owner website as the application URL

#### Scenario: Positive decision has no usable URL
- **WHEN** structured output has `isLinkApplication` equal to `true`
- **AND** neither model output nor owner metadata provides a valid URL
- **THEN** the system does not create a LinkApplication

#### Scenario: Display name fallback
- **WHEN** a positively classified application has no extracted display name
- **THEN** the system uses the owner display name when present
- **AND** otherwise uses the normalized application URL host

#### Scenario: Optional fields are absent
- **WHEN** the model omits or explicitly returns null for logo, description, backlink, or feed URLs
- **THEN** structured-output validation accepts the result
- **AND** the system creates the application when all business-required data is available

#### Scenario: No external enrichment
- **WHEN** model output omits optional application data
- **THEN** the recognition pipeline does not fetch the applicant website to fill missing values

### Requirement: Positive recognition creates a pending application
The system SHALL create a traceable `PENDING` LinkApplication for a valid positive recognition and
SHALL NOT create a formal Link automatically.

#### Scenario: Comment is recognized as a valid application
- **WHEN** a matching Comment is positively classified with a resolved URL and display name
- **THEN** the system creates one LinkApplication with status `PENDING`
- **AND** sets its origin type to `COMMENT`
- **AND** records the source Comment metadata name as `origin.comment.name`
- **AND** copies the owner email locally when the Comment has an email owner
- **AND** does not create a Link

#### Scenario: Same Comment is delivered more than once
- **WHEN** processing is attempted again for a Comment that already produced an application
- **THEN** the system does not create another LinkApplication

#### Scenario: Source Comment changes after creation
- **WHEN** the source Comment is edited or deleted after its application is created
- **THEN** the LinkApplication retains its original extracted application fields
- **AND** continues to reference the original Comment name

### Requirement: Recognition failures are isolated
The system SHALL bound background model execution and SHALL NOT make Comment creation depend on AI
success.

#### Scenario: Recognition succeeds within execution budget
- **WHEN** a queued Comment is analyzed successfully
- **THEN** the single recognition worker proceeds to the next queued Comment

#### Scenario: Transient model call fails
- **WHEN** a model call fails with a retryable error
- **THEN** the system makes at most two retries within a 30-second total timeout

#### Scenario: Recognition ultimately fails
- **WHEN** model resolution, model execution, timeout, or structured-output validation ultimately fails
- **THEN** the system records a structured diagnostic
- **AND** does not create a LinkApplication for that Comment
- **AND** does not retry the Comment after the bounded attempt ends
- **AND** does not affect the already-created Comment

### Requirement: AI Foundation remains optional
The system SHALL keep non-AI link management usable when the compatible optional AI Foundation
plugin is absent, disabled, or has no enabled model-service extension.

#### Scenario: AI Foundation classes are absent
- **WHEN** plugin-links starts without AI Foundation classes on its runtime classpath
- **THEN** plugin-links starts successfully
- **AND** does not register AI-dependent analysis components

#### Scenario: Recognition is configured but not operational
- **WHEN** recognition settings are enabled but AI Foundation or its model-service extension is unavailable
- **THEN** the Link management Console displays a non-blocking warning
- **AND** all non-AI Link and LinkApplication operations remain available

#### Scenario: AI Foundation becomes available later
- **WHEN** AI Foundation becomes operational after a period of unavailability
- **THEN** recognition applies only to Comments created after recognition is operational
- **AND** no missed Comments are scanned

### Requirement: Administrator-initiated comment extraction remains available
The system SHALL preserve the existing administrator-initiated AI extraction tool for pre-filling a
new formal Link.

#### Scenario: Administrator extracts comment fields
- **WHEN** an administrator submits non-empty comment content to the Console extraction operation
- **THEN** the system uses the configured manual-extraction model or AI Foundation default model
- **AND** returns extracted URL, display name, logo, description, and RSS URL fields
- **AND** omitted optional fields do not cause structured-output validation to fail

#### Scenario: Extraction request body is empty
- **WHEN** the Console extraction operation receives no request body or blank comment content
- **THEN** it returns HTTP 400

#### Scenario: Extracted values are applied
- **WHEN** the Console receives a successful extraction result
- **THEN** it pre-fills the existing Link creation form
- **AND** the administrator must still submit the form to create a formal Link
