## MODIFIED Requirements

### Requirement: Explicit verification triggers
The system SHALL run link verification only after an explicit Console trigger, after a successful
Console create/edit operation, after backend LinkApplication approval, or after scheduled automatic
verification is enabled and due.

#### Scenario: Create triggers single-link verification
- **WHEN** a Console user creates a link successfully
- **THEN** the Console triggers verification for the created link without blocking the save flow

#### Scenario: Edit triggers single-link verification
- **WHEN** a Console user edits a link successfully
- **THEN** the Console triggers verification for the edited link without blocking the save flow

#### Scenario: Application approval triggers single-link verification
- **WHEN** backend LinkApplication approval reaches `APPROVED`
- **THEN** the backend triggers verification for the approved Link
- **AND** Console does not need to trigger verification from its approval-success callback

#### Scenario: Approval verification fails
- **WHEN** verification triggered after application approval fails
- **THEN** the Link and `APPROVED` LinkApplication remain persisted
- **AND** the failure is represented by the existing Link verification state or retry behavior

#### Scenario: Manual all-links verification
- **WHEN** a Console user triggers verification without a group or selected link names
- **THEN** the backend starts verification for all existing links asynchronously

#### Scenario: Manual group verification
- **WHEN** a Console user triggers verification with a group name
- **THEN** the backend starts verification only for links in that group asynchronously

#### Scenario: Manual selected-links verification
- **WHEN** a Console user triggers verification with selected link names
- **THEN** the backend starts verification only for those link names asynchronously

#### Scenario: Startup does not trigger verification
- **WHEN** the plugin starts
- **THEN** the system does not immediately verify existing links

#### Scenario: Background schedule does not trigger verification while disabled
- **WHEN** automatic verification is disabled and no Console trigger, save/edit trigger, or
  application-approval trigger occurs
- **THEN** the system does not run recurring scheduled verification

#### Scenario: Background schedule triggers verification while enabled and due
- **WHEN** automatic verification is enabled and the configured interval has elapsed
- **THEN** the system starts a bounded scheduled verification run asynchronously
