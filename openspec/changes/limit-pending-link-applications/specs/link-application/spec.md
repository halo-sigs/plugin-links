## ADDED Requirements

### Requirement: Administrators can configure pending application capacity
The system SHALL expose `application.security.pendingCapacity` as a required positive integer under
a Security subgroup of friend-link application settings and SHALL use `100` as both the settings
schema and backend default.

#### Scenario: Administrator opens enabled application settings
- **WHEN** the friend-link application master switch is enabled
- **THEN** the settings form displays a Security subgroup
- **AND** displays a pending application capacity field with default value `100`
- **AND** explains that new applications pause at the limit until the pending count decreases

#### Scenario: Administrator configures a positive capacity
- **WHEN** the administrator saves a positive integer capacity
- **THEN** the system uses that value for subsequent supported application creation

#### Scenario: Administrator enters a non-positive capacity
- **WHEN** the administrator enters `0` or a negative capacity
- **THEN** settings validation rejects the value
- **AND** `0` is not interpreted as unlimited or disabled

#### Scenario: Capacity has no configured maximum
- **WHEN** the administrator configures a positive integer above the default
- **THEN** the settings schema does not reject it solely for exceeding an artificial maximum

#### Scenario: Application settings have not been persisted
- **WHEN** the application feature uses its initial unsaved settings
- **THEN** the backend pending application capacity is `100`

#### Scenario: Saved capacity is malformed
- **WHEN** the saved pending application capacity is explicitly malformed or non-positive
- **THEN** the system treats new application creation as unavailable
- **AND** does not fall back to an unlimited capacity

### Requirement: Pending capacity governs supported application creation
The system SHALL admit a new LinkApplication through supported plugin creation paths only when the
current number of `PENDING` LinkApplications is strictly less than the effective pending capacity.

#### Scenario: Visitor and Comment applications share capacity
- **WHEN** FORM-origin and COMMENT-origin LinkApplications are pending
- **THEN** both origins contribute to the same pending capacity

#### Scenario: Only pending applications consume capacity
- **WHEN** the system evaluates capacity
- **THEN** it counts LinkApplications with status `PENDING`
- **AND** does not count `APPROVING`, `APPROVED`, or `REJECTED` applications

#### Scenario: Capacity has one remaining slot
- **WHEN** the pending count is one less than the configured capacity
- **THEN** one otherwise valid supported creation may persist a new `PENDING` LinkApplication

#### Scenario: Capacity is exhausted
- **WHEN** the pending count is greater than or equal to the configured capacity
- **THEN** supported creation returns a distinct capacity-reached result
- **AND** does not persist a LinkApplication
- **AND** does not publish a new-application notification

#### Scenario: Capacity is lowered below the current count
- **WHEN** an administrator lowers capacity below the number of existing pending applications
- **THEN** the system preserves every existing application and its current status
- **AND** rejects supported new creation until the pending count becomes lower than the new capacity

#### Scenario: Pending application leaves the queue
- **WHEN** a pending application becomes `APPROVING` or `REJECTED`, or is deleted
- **THEN** it no longer consumes pending capacity

#### Scenario: Authoritative capacity cannot be evaluated
- **WHEN** effective capacity settings or the authoritative pending application query are unavailable
- **THEN** supported creation fails closed
- **AND** does not persist a LinkApplication

#### Scenario: Privileged caller writes directly through the Extension API
- **WHEN** a caller bypasses the plugin's shared creation service and writes a LinkApplication
  directly through Halo's Extension API
- **THEN** this capability does not guarantee enforcement for that write

### Requirement: Single-instance pending capacity is concurrency-safe
The system SHALL serialize authoritative capacity evaluation and persistence across all supported
LinkApplication creation attempts within one plugin instance.

#### Scenario: Different URLs compete for one remaining slot
- **WHEN** two otherwise valid requests with different canonical URLs concurrently observe one
  remaining pending slot
- **THEN** at most one request persists a new `PENDING` LinkApplication
- **AND** the other request receives the capacity-reached result

#### Scenario: Capacity coordination finishes
- **WHEN** an authoritative creation operation succeeds, is rejected, or fails
- **THEN** the process-wide creation gate is released
- **AND** later creation attempts can evaluate current capacity

#### Scenario: Another plugin instance creates an application
- **WHEN** supported creation executes in more than one plugin instance
- **THEN** this capability does not guarantee a distributed hard upper bound

### Requirement: Visitor submissions report pending capacity outcomes
The system SHALL preserve visitor security checks and duplicate behavior before reporting pending
capacity outcomes from `/links/apply`.

#### Scenario: Visitor submission reaches full capacity
- **WHEN** a visitor passes CAPTCHA and the IP submission limit with otherwise valid,
  non-duplicate input
- **AND** pending capacity is exhausted
- **THEN** the system does not persist a LinkApplication
- **AND** redirects to
  `/links?applied=error&message=待审核申请数量已达上限，请稍后再试`
- **AND** the redirect does not include `field`, `value`, or the configured capacity

#### Scenario: Duplicate submission reaches full capacity
- **WHEN** a visitor submission is both a duplicate under existing source-aware rules and received
  while pending capacity is exhausted
- **THEN** the system returns the existing duplicate result instead of the capacity-reached result

#### Scenario: Full-capacity submission consumes abuse controls
- **WHEN** a valid CAPTCHA and IP submission allowance reach authoritative creation while capacity
  is full
- **THEN** the CAPTCHA remains consumed
- **AND** the IP submission allowance remains consumed

#### Scenario: Capacity is full while the theme renders
- **WHEN** the application master and visitor-submission switches are enabled while capacity is full
- **THEN** the template model continues to expose `linkApplicationEnabled` as true
- **AND** the CAPTCHA endpoint remains available according to its existing security limits

#### Scenario: Visitor capacity evaluation fails
- **WHEN** authoritative capacity evaluation fails after visitor abuse controls pass
- **THEN** the system does not persist a LinkApplication
- **AND** redirects to `/links?applied=error&message=暂时无法提交，请稍后再试`
- **AND** the redirect does not include `field` or `value`

#### Scenario: Full capacity is handled normally
- **WHEN** visitor creation returns the capacity-reached result
- **THEN** the system does not emit a warning or error log for that normal result

#### Scenario: Capacity evaluation fails operationally
- **WHEN** visitor capacity evaluation fails because settings or storage are unavailable
- **THEN** the system records an operational diagnostic
- **AND** the diagnostic excludes application fields and other attacker-controlled values
