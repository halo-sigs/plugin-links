# link-verification Specification

## Purpose
Define how links are configured, checked, and displayed for reachability and reciprocal backlink
verification.
## Requirements
### Requirement: Link verification configuration
The system SHALL allow each `Link` to optionally define a backlink scan URL in
`spec.verification.backlinkScanUrl`.

#### Scenario: Link has backlink scan configuration
- **WHEN** a Console user saves a link with a backlink scan URL
- **THEN** the saved `Link` spec contains that URL under `spec.verification.backlinkScanUrl`

#### Scenario: Link has no backlink scan configuration
- **WHEN** a Console user saves a link without a backlink scan URL
- **THEN** the saved `Link` does not require backlink verification configuration

### Requirement: Link verification status
The system SHALL store the latest reachability and backlink verification result under
`Link.status.verification`.

#### Scenario: Reachability verification succeeds
- **WHEN** verification fetches a link URL and the final HTTP response is successful
- **THEN** `status.verification.access.state` is recorded as `ACCESSIBLE`
- **AND** the status records the checked time and final URL

#### Scenario: Reachability verification fails
- **WHEN** verification cannot fetch a link URL or receives a non-success final HTTP response
- **THEN** `status.verification.access.state` is recorded as `INACCESSIBLE`
- **AND** the status records the checked time and failure reason

#### Scenario: Backlink verification finds a reciprocal link
- **WHEN** verification fetches the configured backlink scan URL and finds an HTML anchor pointing
  to the configured Halo external URL
- **THEN** `status.verification.backlink.state` is recorded as `FOUND`
- **AND** the status records the checked time, scan URL, target URL, and matched URL

#### Scenario: Backlink verification does not find a reciprocal link
- **WHEN** verification fetches the configured backlink scan URL but finds no matching HTML anchor
- **THEN** `status.verification.backlink.state` is recorded as `MISSING`
- **AND** the status records the checked time, scan URL, and target URL

#### Scenario: Backlink verification is not configured
- **WHEN** a link has no `spec.verification.backlinkScanUrl`
- **THEN** `status.verification.backlink.state` is recorded as `NOT_CONFIGURED`

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

### Requirement: Verification execution is bounded per link
The system SHALL execute verification in the background with bounded concurrency and per-link
failure isolation.

#### Scenario: Verification request is accepted
- **WHEN** a Console user starts a verification run
- **THEN** the API responds before all remote checks finish
- **AND** the response reports how many links were accepted, skipped, or already running

#### Scenario: One link fails during batch verification
- **WHEN** one link fails during a multi-link verification run
- **THEN** the system records the failure on that link
- **AND** verification continues for the other requested links

#### Scenario: Duplicate running link is skipped
- **WHEN** verification is already running for a link
- **THEN** a new verification request for that same link does not enqueue duplicate work

### Requirement: Console displays verification status
The Console SHALL display reachability and backlink verification status on each `LinkBadge`.

#### Scenario: LinkBadge shows successful states
- **WHEN** a link has accessible reachability status and found backlink status
- **THEN** `LinkBadge` displays both statuses as healthy

#### Scenario: LinkBadge shows failure states
- **WHEN** a link has inaccessible reachability status or missing backlink status
- **THEN** `LinkBadge` displays the corresponding status as unhealthy

#### Scenario: LinkBadge shows unchecked states
- **WHEN** a link has no verification status
- **THEN** `LinkBadge` displays verification status as unknown or absent without implying failure

#### Scenario: LinkBadge explains latest result
- **WHEN** a Console user hovers over a verification status indicator
- **THEN** the tooltip describes the latest result and last checked time when available

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
