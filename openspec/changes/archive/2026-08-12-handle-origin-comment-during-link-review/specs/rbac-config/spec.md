## ADDED Requirements

### Requirement: Source Comment mutations retain Halo Comment permissions
The system SHALL require Halo's existing Comment-management permission for source-Comment approval
and reply actions and SHALL NOT grant that authority through plugin-links' link-management role.

#### Scenario: Reviewer has both permissions
- **WHEN** a reviewer has `plugin:links:manage` and `system:comments:manage`
- **THEN** Console enables the source-Comment actions for an eligible Comment-origin application
- **AND** Halo authorizes the Comment and reply mutations through its existing APIs

#### Scenario: Link manager lacks Comment management
- **WHEN** a reviewer has `plugin:links:manage` without `system:comments:manage`
- **THEN** Console leaves the source preview and general Comment-management link available
- **AND** displays the source-Comment controls as disabled with a permission explanation
- **AND** does not attempt a Comment or reply mutation

#### Scenario: Link role is installed or reconciled
- **WHEN** plugin-links installs or reconciles its link-management RoleTemplate
- **THEN** the role does not depend on, aggregate, or reproduce the Halo Comment-management role

#### Scenario: Permission changes before Comment mutation
- **WHEN** a reviewer loses Comment-management permission after opening the application
- **THEN** Halo rejects the Comment mutation
- **AND** Console reports a Comment-only failure without reverting or misreporting successful link
  approval
