# link-application-notification Specification

## Purpose
Define configurable native Halo notifications for newly persisted friend-link applications,
including recipient subscriptions, lifecycle reconciliation, safe content, and failure isolation.

## Requirements

### Requirement: Administrators can configure new-application notifications
The system SHALL provide a notification subgroup inside the friend-link application settings with
a disabled-by-default enable switch and a multi-user recipient selection.

#### Scenario: Existing installation has no notification settings
- **WHEN** the application settings do not contain a notification subgroup
- **THEN** new-application notifications are treated as disabled
- **AND** no notification Subscription or Reason is created

#### Scenario: Administrator enables notifications
- **WHEN** an administrator enables new-application notifications
- **THEN** the settings form requires at least one selected Halo user
- **AND** accepts multiple selected users

#### Scenario: Recipient selection is not permission-filtered
- **WHEN** an administrator opens the recipient selector
- **THEN** the selector uses Halo's standard selectable users
- **AND** the plugin identifies the field as administrators who should receive notifications
- **AND** the plugin does not enforce a role or `plugin:links:manage` permission check

#### Scenario: Application master switch is disabled
- **WHEN** the friend-link application master switch is disabled
- **THEN** new-application notifications are ineffective regardless of their child switch
- **AND** the configured recipient values are preserved

#### Scenario: Notification switch is disabled
- **WHEN** the notification switch is disabled
- **THEN** new applications do not create notification Reasons
- **AND** the configured recipient values are preserved

#### Scenario: Notification settings cannot be loaded
- **WHEN** notification settings are missing, malformed, or fail to load
- **THEN** notification delivery is treated as disabled
- **AND** application creation remains available according to the existing application settings

### Requirement: Newly persisted applications initiate notifications
The system SHALL attempt one native Halo Reason emission after each new friend-link application is
successfully persisted as `PENDING` while notifications are effective.

#### Scenario: Visitor form creates an application
- **WHEN** a visitor form submission successfully creates a new FORM-origin `PENDING`
  LinkApplication
- **AND** notifications are effective
- **THEN** the system attempts one new-application Reason emission

#### Scenario: Comment recognition creates an application
- **WHEN** Comment recognition successfully creates a new COMMENT-origin `PENDING`
  LinkApplication
- **AND** notifications are effective
- **THEN** the system attempts one new-application Reason emission

#### Scenario: Submission is invalid or duplicate
- **WHEN** application creation returns INVALID or DUPLICATE
- **THEN** the system does not create a new-application Reason

#### Scenario: Notifications are enabled after pending applications already exist
- **WHEN** an administrator enables notifications while existing applications are already present
- **THEN** the system does not backfill Reasons for those existing applications

#### Scenario: Application is created outside the shared service
- **WHEN** a LinkApplication is written directly through the Extension API without using the
  plugin's shared application creation service
- **THEN** this capability does not guarantee a notification for that application

### Requirement: Selected users receive native Halo notifications
The system SHALL use Halo's Subscription, ReasonType, NotificationTemplate, and Reason mechanisms
to notify each distinct, existing selected user according to that user's Halo notification
preferences.

#### Scenario: Multiple valid recipients are selected
- **WHEN** notifications are effective for a newly persisted application
- **AND** multiple distinct selected usernames resolve to Halo users
- **THEN** each resolved user has one matching new-application Subscription
- **AND** Halo resolves those users as recipients of the new-application Reason

#### Scenario: Recipient list contains duplicate usernames
- **WHEN** saved notification settings contain the same non-blank username more than once
- **THEN** the system normalizes it to one effective recipient

#### Scenario: Selected user no longer exists
- **WHEN** a selected username does not resolve to a Halo user
- **THEN** the system skips that username
- **AND** removes any matching stale Subscription for it
- **AND** continues processing other recipients
- **AND** does not rewrite the saved recipient setting

#### Scenario: Selected user loses link-management permission
- **WHEN** a selected existing user no longer has `plugin:links:manage`
- **THEN** the plugin continues to maintain that user's Subscription until the user is removed from
  settings
- **AND** Halo Console authorization independently controls access to `/console/links`

#### Scenario: Selected user changes notification preferences
- **WHEN** a selected user disables this ReasonType or an available notification channel in Halo
- **THEN** delivery follows the user's Halo notification preferences
- **AND** the plugin does not force a station notification or email

### Requirement: Notification subscriptions follow configuration lifecycle
The system SHALL treat the effective notification recipient set as the source of truth for
plugin-managed wildcard LinkApplication Subscriptions.

#### Scenario: Plugin starts with enabled notification settings
- **WHEN** the plugin starts with notifications enabled and selected recipients
- **THEN** the system reconciles matching Subscriptions from the current settings

#### Scenario: Notification settings change
- **WHEN** Halo publishes a plugin configuration update
- **THEN** the system promptly reconciles added and removed recipients

#### Scenario: Application is ready to publish a Reason
- **WHEN** a newly persisted application is about to emit its Reason
- **THEN** the system re-reads effective settings
- **AND** serially reconciles matching Subscriptions before emission

#### Scenario: Recipient is removed
- **WHEN** a username is removed from the effective recipient set
- **THEN** the system removes that user's matching new-application wildcard Subscription
- **AND** later new applications no longer target that user

#### Scenario: Notifications become ineffective
- **WHEN** the application master switch or notification switch is disabled
- **THEN** the system reconciles to no active new-application Subscriptions
- **AND** preserves the configured recipient values

#### Scenario: Plugin stops
- **WHEN** the plugin stops
- **THEN** the system removes its dynamic new-application Subscriptions
- **AND** does not delete previously created Notifications

### Requirement: Notifications contain minimal safe review context
The system SHALL provide a default Chinese notification template with escaped plain-text and HTML
content that identifies the application and links to the existing Links Console page.

#### Scenario: Form-origin notification is rendered
- **WHEN** a notification is rendered for a FORM-origin application
- **THEN** its title is `收到新的友链申请：{网站名称}`
- **AND** its body contains the escaped website name and website address
- **AND** labels the source as `访客自助申请`
- **AND** provides an `前往审核` action targeting `/console/links`

#### Scenario: Comment-origin notification is rendered
- **WHEN** a notification is rendered for a COMMENT-origin application
- **THEN** its body labels the source as `评论识别`
- **AND** does not include the source Comment metadata name or raw Comment content

#### Scenario: Application contains hostile display text
- **WHEN** an application-controlled value contains HTML or executable markup
- **THEN** the HTML template renders that value as escaped text
- **AND** does not interpret it as template or markup content

#### Scenario: Notification content is inspected
- **WHEN** a recipient reads the notification
- **THEN** it does not expose the applicant email, description, raw Comment, or internal resource
  metadata name

#### Scenario: Recipient opens the review action
- **WHEN** a recipient activates the notification review action
- **THEN** the recipient is taken to `/console/links`
- **AND** the plugin does not attempt to open a specific application detail view

### Requirement: Notification failures do not change application creation
The system SHALL isolate settings, subscription, and Reason publication failures from an
application that has already been persisted.

#### Scenario: Subscription reconciliation fails
- **WHEN** Subscription reconciliation fails after a new application is persisted
- **THEN** the system logs the failure
- **AND** returns the application creation result as CREATED

#### Scenario: Reason emission fails
- **WHEN** Reason emission fails after a new application is persisted
- **THEN** the system logs the failure
- **AND** returns the application creation result as CREATED
- **AND** does not delete or roll back the LinkApplication

#### Scenario: Halo retries notification processing
- **WHEN** Halo retries Reason reconciliation or an external notifier after a partial failure
- **THEN** the plugin does not promise strict exactly-once channel delivery
- **AND** the plugin does not initiate an additional emission for the same successful create call
