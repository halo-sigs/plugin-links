## Context

Form submissions and Comment recognition both create applications through
`LinkApplicationService.create`. The durable boundary is the successful
`ReactiveExtensionClient.create` of a `PENDING` `LinkApplication`; invalid and duplicate
submissions never cross that boundary.

Halo notifications do not accept recipients directly on a Reason. A plugin declares a
ReasonType and NotificationTemplate, maintains a Subscription for each intended Halo user, and
emits a Reason whose recipients are resolved asynchronously from those subscriptions. The existing
application settings already contain nested groups, and Halo exposes plugin configuration update
events that can drive subscription reconciliation.

The implementation must remain reactive on the application creation path, treat all submitted
application fields as untrusted, preserve compatibility with settings saved before this change,
and use the existing `/console/links` destination because applications do not have a routable
Console detail view.

## Goals / Non-Goals

**Goals:**

- Let an administrator enable new-application notifications and select multiple Halo users.
- Cover both form-origin and Comment-origin applications at their shared durable creation
  boundary.
- Use Halo's native notification resources, delivery channels, and per-user preferences.
- Keep selected-user subscriptions aligned with settings across startup, updates, publication,
  disablement, and plugin stop.
- Isolate all notification failures from application creation.
- Provide safe default Chinese plain-text and HTML notification content linked to
  `/console/links`.

**Non-Goals:**

- Notify applicants when they submit, are approved, or are rejected.
- Notify administrators about approval-state changes.
- Backfill existing applications or observe LinkApplications created directly through the
  Extension API.
- Add a Console route, query deep link, custom recipient selector, digest, aggregation, frequency
  limiting, English template, or strict exactly-once channel delivery.

## Decisions

### 1. Extend the application settings with a fail-closed notification subgroup

Add `application.notification.enabled` and `application.notification.recipients`. The schema uses
Halo's standard multi-user selector, labels the values as notification administrators, and requires
at least one recipient while the notification switch is enabled. It does not implement a custom
administrator filter.

The settings DTO defaults to `enabled: false` and an empty recipient collection, trims and
deduplicates usernames, and treats missing or unreadable values as disabled. Notification
effectiveness requires the application master switch, the notification switch, and a non-empty
recipient set. Disabling either switch preserves configured usernames but removes active
subscriptions.

This preserves old ConfigMaps without migration and avoids introducing notification delivery
during an upgrade. A custom permission-aware selector was rejected because Halo permissions may
come from different roles and change after selection.

### 2. Publish only after the shared service durably creates an application

Add a focused notification publisher invoked after `client.create(application)` succeeds inside
`LinkApplicationService`. The publisher is part of the same reactive chain: it attempts
subscription reconciliation and Reason creation before returning the created result, catches and
logs notification errors, and always returns the already-created application.

This covers FORM and COMMENT without duplicating logic and ensures INVALID and DUPLICATE results do
not notify. A LinkApplication reconciler was rejected because observing arbitrary Extension writes
would require durable notification idempotency and startup replay semantics that are outside the
requested scope. Detached fire-and-forget publication was rejected because shutdown could silently
lose an in-flight publication attempt.

### 3. Use one native ReasonType and one default Chinese template

Declare `plugin-links-new-link-application` as both the globally unique ReasonType and template
resource name. The ReasonType is visible in personal notification preferences to users with
`plugin:links:manage`.

Each emitted Reason uses:

- the system identity as author;
- the concrete LinkApplication GVK and metadata name as subject identity;
- the application display name as subject title;
- an absolute `/console/links` management URL as subject URL and template data;
- escaped display name, website URL, and localized origin label as template data.

The default Chinese title is `收到新的友链申请：{网站名称}`. Plain-text and HTML bodies contain the
website name, clickable website address, `访客自助申请` or `评论识别` source, and an `前往审核` action.
The template must use escaped text bindings for all application-controlled values.

Using native resources preserves Halo's standard station and external notifier behavior. Selected
users may still disable this ReasonType or individual channels through their personal preferences;
the plugin does not write Notifications directly or force email delivery.

### 4. Treat settings as the source of truth for wildcard subscriptions

For each valid selected username, maintain one Subscription for the notification ReasonType and a
`LinkApplication` subject containing apiVersion and kind but no name. The wildcard subject lets a
single per-user subscription match every concrete application Reason. The plugin owns subscriptions
with this exact ReasonType and wildcard subject shape; reconciliation removes matching
subscriptions for usernames no longer selected and idempotently subscribes the current set.

All reconciliations use one process-local serialization boundary so setting updates and concurrent
application creations cannot run competing unsubscribe/subscribe sequences. Reconciliation runs:

1. when the plugin starts, to restore subscriptions from current settings;
2. on `PluginConfigUpdatedEvent`, to apply setting changes promptly;
3. immediately before each Reason emission, to recover from drift and use the latest readable
   settings;
4. with an empty effective recipient set when either switch is disabled;
5. during plugin stop, to remove dynamic resources that Halo's declarative resource cleanup does
   not own.

Lifecycle callbacks may wait with a bounded timeout because Halo's start and stop contracts are
synchronous; the application request path remains reactive. Historical Notifications are user
data and are never deleted by this cleanup.

Before subscribing, reconciliation checks that selected usernames still resolve to Halo users.
Missing users are skipped, any stale matching subscription is removed, and a warning is logged
without rewriting saved settings. The plugin deliberately does not re-check
`plugin:links:manage`; a selected existing user continues receiving according to personal
preferences until removed from settings, while Console authorization still guards the target page.

### 5. Define delivery as best-effort without changing creation semantics

For each effective successful creation, the plugin initiates one Reason emission. It does not add a
plugin-level delivery retry, outbox, or per-application sent marker. Halo owns asynchronous
recipient resolution, template rendering, channel retries, and user-level deduplication.

Settings loading, user resolution, subscription, template registration, or Reason emission failure
is logged and swallowed after the application has been persisted. This prevents a visitor from
seeing a failed submission while a reviewable application already exists.

Because Halo resolves recipients asynchronously, a settings change racing immediately after Reason
creation may affect that one notification. Core reconciliation or notifier retries may also produce
duplicate channel deliveries after partial failure. These limitations are accepted under the
best-effort contract.

## Risks / Trade-offs

- [A selected user loses administrator permissions but continues receiving] → Keep notification
  content minimal, rely on Console authorization, and clearly label the selector for
  administrators.
- [A deleted user remains in saved settings] → Remove any matching subscription, skip delivery,
  log the stale username, and leave cleanup visible to the setting owner.
- [Configuration and Reason processing race] → Serialize plugin reconciliation and re-check before
  emission; document that Halo's later asynchronous resolution is still best-effort.
- [Untrusted application content reaches an HTML notifier] → Use escaped template bindings only
  and cover hostile values with a template smoke test.
- [Lifecycle cleanup times out or fails] → Bound synchronous lifecycle waits, log failures, and
  reconcile again on the next startup or publication.
- [Halo retries after partial delivery] → Emit once from the plugin and avoid promising strict
  exactly-once delivery.

## Migration Plan

1. Ship the disabled settings subgroup and declarative notification resources.
2. On first startup, normalize missing notification settings to disabled and reconcile to an empty
   subscription set.
3. When an administrator enables notifications and saves recipients, reconcile subscriptions
   immediately; only later successful applications notify.
4. On rollback or plugin stop, remove plugin-owned dynamic subscriptions. Existing application
   data, saved recipient values, and historical Notifications remain safe; older plugin versions
   ignore the additional setting fields.

## Open Questions

None.
