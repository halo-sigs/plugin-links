## ADDED Requirements

### Requirement: Automatic verification settings
The system SHALL expose plugin settings that control scheduled automatic link verification.

#### Scenario: Automatic verification is disabled by default
- **WHEN** the plugin settings have not been customized
- **THEN** scheduled automatic link verification is disabled

#### Scenario: Configure automatic verification cadence
- **WHEN** an administrator configures the automatic verification interval
- **THEN** the system uses that interval to decide when a scheduled verification run is due

#### Scenario: Configure automatic verification batch size
- **WHEN** an administrator configures the maximum links per automatic run
- **THEN** one scheduled run enqueues no more than that number of links

#### Scenario: Configure backlink checks for automatic runs
- **WHEN** an administrator enables automatic verification and enables backlink checks
- **THEN** scheduled automatic verification includes reciprocal backlink checks for links with
  `spec.verification.backlinkScanUrl`

#### Scenario: Automatic runs omit backlink checks by default
- **WHEN** automatic verification is enabled but backlink checks are not enabled
- **THEN** scheduled automatic verification checks link reachability only
- **AND** preserves each link's existing backlink verification status

### Requirement: Scheduled automatic verification execution
The system SHALL execute scheduled automatic verification only when enabled and only after the
configured interval has elapsed.

#### Scenario: Disabled automatic verification does not enqueue work
- **WHEN** the automatic verification setting is disabled
- **THEN** the scheduler does not enqueue link verification work

#### Scenario: Scheduled run enqueues a bounded batch
- **WHEN** automatic verification is enabled and the configured interval has elapsed
- **THEN** the scheduler selects existing links for verification
- **AND** enqueues no more than the configured maximum links per run

#### Scenario: Scheduled run prefers stale links
- **WHEN** more links exist than the configured maximum links per run
- **THEN** the scheduler prioritizes links that have never been checked or were checked least
  recently

#### Scenario: Scheduled run reuses verification safeguards
- **WHEN** a scheduled automatic run enqueues links
- **THEN** the system applies the same per-link deduplication, failure isolation, and SSRF
  protections used by manual verification

## MODIFIED Requirements

### Requirement: Explicit verification triggers
The system SHALL run link verification only after an explicit Console trigger, after a successful
Console create/edit operation, or after scheduled automatic verification is enabled and due.

#### Scenario: Create triggers single-link verification
- **WHEN** a Console user creates a link successfully
- **THEN** the Console triggers verification for the created link without blocking the save flow

#### Scenario: Edit triggers single-link verification
- **WHEN** a Console user edits a link successfully
- **THEN** the Console triggers verification for the edited link without blocking the save flow

#### Scenario: Manual all-links verification
- **WHEN** a Console user triggers verification without a group or selected link names
- **THEN** the backend starts verification for all existing links asynchronously

#### Scenario: Manual group verification
- **WHEN** a Console user triggers verification with a group name
- **THEN** the backend starts verification only for links in that group asynchronously

#### Scenario: Manual selected-links verification
- **WHEN** a Console user triggers verification with selected link names
- **THEN** the backend starts verification only for those links asynchronously

#### Scenario: Startup does not trigger verification
- **WHEN** the plugin starts
- **THEN** the system does not immediately verify existing links

#### Scenario: Background schedule does not trigger verification while disabled
- **WHEN** automatic verification is disabled and no Console trigger or save/edit trigger occurs
- **THEN** the system does not run recurring scheduled link verification

#### Scenario: Background schedule triggers verification while enabled and due
- **WHEN** automatic verification is enabled and the configured interval has elapsed
- **THEN** the system starts a bounded scheduled verification run asynchronously
