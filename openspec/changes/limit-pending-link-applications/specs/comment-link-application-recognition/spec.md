## ADDED Requirements

### Requirement: Comment recognition avoids work while pending capacity is unavailable
The system SHALL perform a non-authoritative pending-capacity precheck before invoking AI for a
matching new Comment and SHALL fail closed when that precheck cannot establish available capacity.

#### Scenario: Pending capacity is already exhausted
- **WHEN** a matching new Comment reaches recognition while the pending count is greater than or
  equal to capacity
- **THEN** the system skips the Comment without invoking the model
- **AND** does not create a LinkApplication

#### Scenario: Capacity precheck cannot be evaluated
- **WHEN** effective capacity settings or the indexed pending-count query are unavailable before
  model invocation
- **THEN** the system skips model invocation
- **AND** does not create a LinkApplication
- **AND** records an operational diagnostic without raw Comment content

#### Scenario: Capacity is available before recognition
- **WHEN** the capacity precheck reports an available slot
- **THEN** an otherwise eligible Comment may proceed through the existing AI recognition flow
- **AND** the precheck does not reserve a slot

#### Scenario: Comment is skipped for capacity
- **WHEN** recognition skips a Comment because capacity is full or unavailable
- **THEN** the system does not retry that Comment
- **AND** does not backfill it after capacity becomes available

## MODIFIED Requirements

### Requirement: Positive recognition creates a pending application
The system SHALL create a traceable `PENDING` LinkApplication for a valid positive recognition only
when the shared pending capacity remains available at authoritative creation time and SHALL NOT
create a formal Link automatically.

#### Scenario: Comment is recognized as a valid application
- **WHEN** a matching Comment is positively classified with a resolved URL and display name
- **AND** authoritative pending capacity remains available
- **THEN** the system creates one LinkApplication with status `PENDING`
- **AND** sets its origin type to `COMMENT`
- **AND** records the source Comment metadata name as `origin.comment.name`
- **AND** copies the owner email locally when the Comment has an email owner
- **AND** does not create a Link

#### Scenario: Capacity is consumed during recognition
- **WHEN** the capacity precheck allows model invocation
- **AND** another supported creation consumes the final slot before authoritative creation
- **THEN** the system does not create a LinkApplication
- **AND** records a capacity-skipped outcome without treating normal fullness as an operational error
- **AND** does not retry or backfill the Comment

#### Scenario: Authoritative capacity evaluation fails after recognition
- **WHEN** a Comment is positively classified
- **AND** authoritative capacity evaluation is unavailable before persistence
- **THEN** the system does not create a LinkApplication
- **AND** records an operational diagnostic without raw Comment content
- **AND** does not retry or backfill the Comment

#### Scenario: Same Comment is delivered more than once
- **WHEN** processing is attempted again for a Comment that already produced an application
- **THEN** the system does not create another LinkApplication

#### Scenario: Source Comment changes after creation
- **WHEN** the source Comment is edited or deleted after its application is created
- **THEN** the LinkApplication retains its original extracted application fields
- **AND** continues to reference the original Comment name
