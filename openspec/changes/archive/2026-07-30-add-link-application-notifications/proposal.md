## Why

New friend-link applications currently enter the review queue without notifying anyone, so
administrators must discover them by revisiting the Links Console page. The plugin should use
Halo's notification system to alert explicitly selected administrators whenever a new application
is successfully created.

## What Changes

- Add a disabled-by-default notification subgroup to the existing friend-link application
  settings, with an enable switch and a required multi-user recipient selector.
- Notify all selected recipients for newly created form-origin and Comment-origin applications.
- Publish notifications through Halo's native ReasonType, NotificationTemplate, Subscription, and
  Reason mechanisms while respecting each recipient's notification preferences.
- Keep application creation successful when settings, subscription, or notification publication
  fails, and never notify for invalid, duplicate, or historical applications.
- Reconcile plugin-managed subscriptions when the plugin starts, settings change, and immediately
  before publication, and remove them when notifications or the plugin are disabled.
- Link notifications to `/console/links` without adding a new Console route or application detail
  deep link.

## Capabilities

### New Capabilities

- `link-application-notification`: Configure selected notification recipients and deliver
  best-effort Halo notifications for newly persisted friend-link applications.

### Modified Capabilities

None.

## Impact

- Backend: application settings DTO/fetching, the shared application creation flow, notification
  publication and subscription lifecycle, plugin startup/stop handling, and tests.
- Console settings: the declarative settings schema gains a nested notification group; no custom
  Console component or generated API client change is required.
- Plugin resources: add a ReasonType and a default Chinese NotificationTemplate.
- Halo integration: use the notification APIs already provided by the minimum supported Halo
  version; no new external dependency or public endpoint is introduced.
