## 1. Notification Settings

- [x] 1.1 Add settings tests for disabled defaults, legacy configurations, recipient trimming and
  deduplication, master-switch dependency, and enabled-without-recipients fail-closed behavior.
- [x] 1.2 Extend `LinkApplicationSettings` with the normalized notification switch, recipient set,
  and effective-state helpers required by the tests.
- [x] 1.3 Add the nested notification group to `settings.yaml` using Halo's multi-user selector,
  conditional required validation, administrator-focused labels, and preserved values while
  disabled.

## 2. Native Notification Resources

- [x] 2.1 Add the `plugin-links-new-link-application` ReasonType and default Chinese
  NotificationTemplate with `plugin:links:manage` UI permission, plain-text content, and escaped
  HTML content.
- [x] 2.2 Add a resource/template smoke test that locks required Reason attributes, FORM and
  COMMENT source labels, the `/console/links` action, omitted private fields, and escaping of
  hostile application values.

## 3. Subscription Lifecycle

- [x] 3.1 Add subscription manager tests for wildcard LinkApplication interest reasons,
  username deduplication, missing-user filtering, added and removed recipients, disabled settings,
  idempotent repeated reconciliation, and serialized concurrent reconciliation.
- [x] 3.2 Implement the reactive subscription manager with the notification settings as its source
  of truth and exact ReasonType plus wildcard LinkApplication subject matching.
- [x] 3.3 Add lifecycle tests for startup restoration, `PluginConfigUpdatedEvent` reconciliation,
  pre-publication reconciliation, bounded stop cleanup, and preservation of historical
  Notifications.
- [x] 3.4 Wire startup, configuration-update, and plugin-stop lifecycle hooks to the subscription
  manager without blocking the application request path.

## 4. Application Notification Publication

- [x] 4.1 Add publisher tests for the system author, concrete LinkApplication subject, absolute
  Console URL, minimal template attributes, distinct origin labels, disabled settings, and one
  Reason emission per call.
- [x] 4.2 Implement the reactive application notification publisher so it reconciles current
  subscriptions before emitting `plugin-links-new-link-application`.
- [x] 4.3 Extend `LinkApplicationService` tests to prove FORM and COMMENT durable creates notify,
  INVALID and DUPLICATE results do not notify, and subscription or Reason failures still return
  CREATED without deleting the application.
- [x] 4.4 Invoke the publisher only after `ReactiveExtensionClient.create` succeeds in the shared
  application service, with warning logs and error isolation around the notification stage.

## 5. Verification

- [x] 5.1 Run the targeted settings, subscription lifecycle, publisher, application service,
  Router, and Comment recognition tests and fix only failures introduced by this change.
- [x] 5.2 Run `./gradlew test` and `./gradlew build` to verify the complete backend and bundled
  Console lifecycle.
- [x] 5.3 Run `openspec validate add-link-application-notifications --strict` and confirm the
  worktree contains only the intended implementation, tests, resources, and change artifacts.
