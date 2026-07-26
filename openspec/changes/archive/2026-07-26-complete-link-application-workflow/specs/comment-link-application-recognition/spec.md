## MODIFIED Requirements

### Requirement: Administrators can configure comment application recognition
The system SHALL configure AI-powered friend-link application recognition inside the Link
Application settings group and SHALL require the application master switch, recognition child
switch, model, and explicit Comment subjects to be effective.

#### Scenario: Recognition is disabled by default
- **WHEN** the plugin is installed or upgraded without an existing application configuration
- **THEN** the application master switch is disabled
- **AND** automatic Comment application recognition is disabled

#### Scenario: Recognition settings are colocated with applications
- **WHEN** an administrator opens Link Application settings
- **THEN** the settings contain the application master switch and Comment-recognition child controls
- **AND** the recognition switch, model, and sources are not controlled by the separate AI group
  master switch

#### Scenario: Recognition child settings are incomplete
- **WHEN** the application master switch or recognition child switch is disabled
- **OR** no model or no valid source is selected
- **THEN** automatic Comment application recognition is not effective

#### Scenario: Configure links page source
- **WHEN** an administrator adds a recognition source with type `LINKS`
- **THEN** new Comments whose subject is the current PluginLinks links page are eligible for
  recognition
- **AND** no additional subject selection is required

#### Scenario: Configure post source
- **WHEN** an administrator adds a recognition source with type `POST`
- **THEN** the administrator must select exactly one post
- **AND** only new Comments whose subject is that post are eligible for recognition

#### Scenario: Configure single-page source
- **WHEN** an administrator adds a recognition source with type `SINGLE_PAGE`
- **THEN** the administrator must select exactly one single page
- **AND** only new Comments whose subject is that single page are eligible for recognition

#### Scenario: Duplicate sources are configured
- **WHEN** the same Comment subject appears more than once in the configured source list
- **THEN** the system treats the entries as one source
- **AND** a new Comment triggers at most one model analysis

#### Scenario: Recognition privacy disclosure is shown
- **WHEN** an administrator configures Comment recognition
- **THEN** the settings explain that matching new top-level Comments send raw content to the selected
  AI model
- **AND** state that hidden and unapproved Comments remain eligible

#### Scenario: Required runtime integration is unavailable
- **WHEN** recognition is configured as effective but AI Foundation integration is unavailable
- **THEN** the system does not analyze new Comments
- **AND** non-AI link and application management remain operational

### Requirement: AI Foundation remains optional
The system SHALL keep non-AI link and LinkApplication management usable when the compatible
optional AI Foundation plugin is absent, disabled, or has no enabled model-service extension.

#### Scenario: AI Foundation classes are absent
- **WHEN** plugin-links starts without AI Foundation classes on its runtime classpath
- **THEN** plugin-links starts successfully
- **AND** does not register AI-dependent analysis components

#### Scenario: Effective recognition is not operational
- **WHEN** the application master switch and Comment-recognition child switch are enabled
- **AND** AI Foundation or the selected model-service extension is unavailable
- **THEN** the Link management Console displays a non-blocking warning
- **AND** all non-AI Link and LinkApplication operations remain available

#### Scenario: Recognition is disabled by application settings
- **WHEN** the application master switch or Comment-recognition child switch is disabled
- **THEN** Console does not display an AI operational warning for Comment application recognition

#### Scenario: AI Foundation becomes available later
- **WHEN** AI Foundation becomes operational after a period of unavailability
- **THEN** recognition applies only to Comments created after recognition is operational
- **AND** no missed Comments are scanned
