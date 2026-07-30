## Context

Visitor forms and Comment recognition are the two supported LinkApplication creation paths. Both
eventually call `LinkApplicationService`, which validates input, lists existing Links and
LinkApplications for source-aware duplicate detection, persists one `PENDING` application, and
publishes a notification after successful persistence.

Existing application creation is serialized by canonical URL within one plugin process. That
prevents duplicate creation for the same URL, but two different URLs can concurrently observe 99
pending applications and both create when the configured capacity is 100. Capacity therefore needs
one additional process-wide creation boundary around the authoritative count-and-create operation.

`spec.status` is indexed, and the existing history API already exposes a server-reported total for
`PENDING`. Comment recognition runs only for newly delivered Comments, uses one worker, and does not
backfill Comments missed while recognition is unavailable.

## Goals / Non-Goals

**Goals:**

- Add a positive pending-review capacity setting with a backend and settings-schema default of 100.
- Share one capacity across FORM and COMMENT origins while counting only `PENDING` applications.
- Enforce a hard upper bound across supported creation paths and different URLs in one plugin
  instance.
- Preserve duplicate semantics, visitor security checks, existing theme availability, and
  successful-creation notifications.
- Avoid unnecessary Comment AI calls while full and fail closed when capacity cannot be evaluated.
- Keep capacity rejection a normal, non-logging control flow.

**Non-Goals:**

- Cross-instance distributed coordination or a persisted capacity counter.
- Admission control for privileged direct writes through Halo's generic Extension API.
- Per-origin quotas, rate limits, automatic cleanup, or automatic rejection of existing
  applications.
- A Console capacity progress indicator, a new generated API, or a persisted LinkApplication field.
- Compatibility branches, setting migration, data migration, or legacy-setting tests for the
  unreleased application feature.

## Decisions

### 1. Add one required positive setting under Application Security

Add `application.security.pendingCapacity` as a required number with minimum `1`, default `100`,
and help text explaining that new applications pause while the pending count is at the limit. The
Security subgroup follows the application master switch, like the other application child groups.

`LinkApplicationSettings` owns the same default so behavior does not depend on whether the settings
form has been saved. A missing value uses the feature default. An explicitly non-positive or
otherwise malformed value makes application settings unavailable and preserves the current
fail-closed behavior for new creation.

There is no maximum value and `0` has no special meaning. Administrators use the existing master
switch to stop all new applications.

**Alternatives considered:**

- Treating `0` as unlimited creates a dangerous typo path for a security control.
- Treating `0` as disabled duplicates the existing master switch.
- Adding a maximum prevents administrators from deliberately choosing a larger review queue
  without improving enforcement.

### 2. Define capacity as the current number of PENDING applications

FORM and COMMENT origins use the same pool. Only `spec.status == PENDING` consumes capacity;
`APPROVING`, `APPROVED`, and `REJECTED` do not. Approval reservation, rejection, or deletion of a
pending application therefore releases capacity without maintaining a separate counter.

Lowering the setting below the current count does not mutate existing resources. New creation
remains blocked until the count becomes strictly less than the new value.

The authoritative creation path reuses the LinkApplication list already required for duplicate
detection. Duplicate detection runs before capacity evaluation, so an already-submitted URL keeps
its existing duplicate result even while the queue is full.

**Alternatives considered:**

- Counting `APPROVING` conflates work already accepted by an administrator with the waiting review
  backlog.
- A persisted counter creates reconciliation, rollback, and crash-consistency work for data already
  represented by indexed resources.
- Automatically deleting or rejecting excess records would destroy administrator-owned review
  data.

### 3. Add a process-wide creation gate around authoritative enforcement

All supported `LinkApplicationService.create` operations enter one process-wide asynchronous gate
before the existing canonical-URL coordination and remain inside it through settings evaluation,
duplicate detection, pending counting, persistence, and result production. The existing per-URL
coordination remains because approval reservation also uses it to protect URL occupancy.

The lock order is always process-wide creation gate followed by canonical-URL coordination.
Approval does not acquire the creation gate and can only reduce the pending count, so it cannot
cause an overflow or form a reverse lock cycle. The gate releases on success, rejection, or error
and does not retain per-request keys.

This guarantees that two different URLs competing for one remaining slot can produce at most one
new application in one plugin instance. Direct Extension writes and other plugin instances remain
outside the guarantee.

**Alternatives considered:**

- A count followed by create under only the URL-specific gate permits different URLs to exceed the
  limit.
- A database-style reservation resource or distributed lock expands the change beyond Halo's
  current single-instance application-creation contract.
- Replacing URL coordination entirely would weaken its existing interaction with approval
  reservation.

### 4. Represent full capacity as a normal creation result

Add a distinct capacity-reached result to shared application creation. It does not persist an
application and therefore never invokes the notification publisher. Settings or storage failures
remain operational failures rather than being mislabeled as normal capacity exhaustion.

Normal full-capacity results do not produce warning logs. Actual settings or pending-count failures
fail closed and produce an operational error without application fields, URLs, Comment content, or
other attacker-controlled values.

**Alternatives considered:**

- Mapping full capacity to INVALID loses the distinction between applicant input and server policy.
- Throwing for normal fullness would create noisy failure diagnostics under intentional abuse.
- Publishing a capacity notification provides attackers with a notification-spam mechanism.

### 5. Preserve visitor security ordering and theme availability

The effective visitor sequence remains:

1. application and visitor-channel settings;
2. form parsing and CAPTCHA consumption;
3. the one-minute IP submission allowance;
4. shared validation and duplicate detection;
5. authoritative capacity evaluation;
6. persistence and success notification.

A full queue redirects to:

`/links?applied=error&message=待审核申请数量已达上限，请稍后再试`

The redirect includes neither `field`, `value`, nor the configured capacity. The valid CAPTCHA and
IP allowance remain consumed, so fullness does not create a free high-frequency probing path.

If authoritative capacity evaluation fails, the visitor receives:

`/links?applied=error&message=暂时无法提交，请稍后再试`

The template model continues to expose `linkApplicationEnabled` from the master and visitor
switches only. `GET /links/captcha` also stays available while full because capacity can be released
at any time and themes should not depend on a dynamic availability contract.

### 6. Use a non-authoritative precheck to avoid unnecessary Comment AI calls

Before invoking AI, Comment recognition reads the effective capacity and queries the indexed
`PENDING` total. If the queue is full, or capacity cannot be evaluated, it skips model invocation
and does not create an application.

The precheck is only a cost optimization. After a positive model result, the shared service enters
the global gate and repeats the authoritative evaluation. If another source consumes the last slot
between the checks, the Comment produces a capacity-skipped outcome.

Comments skipped before or after AI are not retried or backfilled. This preserves the current
new-Comment-only lifecycle and prevents a later capacity release from triggering a burst of old
model calls.

**Alternatives considered:**

- Checking only after AI enforces storage capacity but wastes model calls while the queue is known
  to be full.
- Relying only on the precheck is race-prone and cannot enforce a hard upper bound.
- Queueing skipped Comments introduces a new durable retry lifecycle and contradicts the current
  no-backfill contract.

## Risks / Trade-offs

- [Risk] A process-wide gate reduces creation concurrency.
  → Mitigation: application creation is low-volume administrative intake, and the existing
  duplicate path already lists all applications; correctness at the configured boundary is more
  important than parallel throughput.
- [Risk] A direct Extension write or another plugin instance can exceed the configured value.
  → Mitigation: document the supported-path, single-instance boundary; broader admission control is
  explicitly out of scope.
- [Risk] Comment precheck can skip a Comment immediately before capacity is released.
  → Mitigation: accept this conservative fail-closed race and preserve the established no-retry,
  no-backfill model.
- [Risk] Storage or settings outages temporarily reject legitimate applications.
  → Mitigation: use a generic visitor error, emit aggregate operational diagnostics, and never
  weaken the security limit on evaluation failure.
- [Risk] A very large administrator-selected value weakens backlog protection.
  → Mitigation: provide a safe default and clear help text while leaving the explicit policy choice
  to the administrator.

## Migration Plan

No migration is required because the friend-link application feature has not been released. The
setting, backend default, enforcement, theme documentation, and tests land as one initial contract.
Rollback removes the guard and setting without transforming LinkApplication resources.

## Open Questions

None. Capacity scope, counted state, concurrency boundary, visitor behavior, Comment behavior,
failure handling, configuration semantics, and compatibility scope were confirmed during
exploration.
