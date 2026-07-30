## 1. Application Security Setting

- [x] 1.1 Extend settings tests first for the backend default `100`, a positive custom value,
  rejected non-positive or malformed values, and fail-closed application creation when effective
  capacity is unavailable.
- [x] 1.2 Add the conditional Application Security subgroup and required
  `security.pendingCapacity` number field with minimum `1`, default `100`, and the confirmed help
  text.
- [x] 1.3 Extend `LinkApplicationSettings` and its fetcher with the Security child settings,
  backend default, positive-value validation, and no legacy compatibility or migration branch.

## 2. Shared Capacity Evaluation

- [x] 2.1 Add focused capacity-evaluator tests proving FORM and COMMENT origins share one pool,
  only `PENDING` counts, 99/100 is available, 100/100 is full, a lowered limit preserves existing
  data, and settings or indexed-count failures fail closed.
- [x] 2.2 Implement reusable capacity evaluation that uses the `spec.status=PENDING` index for the
  Comment precheck and can evaluate the already-loaded LinkApplication list during authoritative
  creation.
- [x] 2.3 Verify the evaluator introduces no persisted counter, LinkApplication field, generated
  API change, direct Extension API admission control, or distributed coordination.

## 3. Authoritative Creation and Concurrency

- [x] 3.1 Extend `LinkApplicationService` tests first for the distinct capacity-reached result,
  duplicate-before-capacity ordering, no persistence or notification while full, operational
  failure propagation, and capacity release after pending applications leave the queue.
- [x] 3.2 Add a concurrent different-URL test proving that two requests competing for one remaining
  slot persist at most one application, while existing same-URL FORM/COMMENT and approval
  coordination tests remain green.
- [x] 3.3 Add the process-wide asynchronous creation gate, acquire it before canonical-URL
  coordination, release it on every terminal signal, and keep authoritative settings, duplicate,
  count, and create work inside the guarded operation.
- [x] 3.4 Return capacity exhaustion as normal control flow, keep settings/storage failures
  operational, and ensure only successful persistence invokes the notification publisher.

## 4. Visitor Submission Contract

- [x] 4.1 Extend router tests first for the exact full-capacity 303 redirect, the exact temporary
  failure redirect, absence of `field`, `value`, and capacity disclosure, and preserved duplicate
  precedence.
- [x] 4.2 Add ordering tests proving full-capacity requests consume valid CAPTCHA and IP allowances,
  while `linkApplicationEnabled` and `GET /links/captcha` remain governed only by their existing
  switches and security limits.
- [x] 4.3 Map shared capacity results and operational failures in `LinkRouter`, suppress logs for
  normal fullness, and emit only aggregate diagnostics without submitted application values for
  real failures.
- [x] 4.4 Update `dev/theme-api.md` with the capacity-full and temporarily-unavailable redirects,
  retained CAPTCHA behavior, and the absence of a dynamic capacity value in the theme model.

## 5. Comment Recognition Contract

- [x] 5.1 Extend Comment recognition tests first for full-capacity precheck without an AI call,
  unavailable-precheck fail-closed behavior, available non-reserving precheck, and diagnostics that
  exclude raw Comment content.
- [x] 5.2 Add the indexed precheck before AI invocation, then map authoritative capacity exhaustion
  after a positive result to a capacity-skipped outcome without warning-level logging.
- [x] 5.3 Add race and lifecycle tests proving a slot consumed during AI work prevents persistence
  and that capacity-skipped or capacity-failed Comments are never retried or backfilled.

## 6. Validation

- [x] 6.1 Run focused settings, capacity, service concurrency, route, Comment recognition, and
  notification tests and confirm every new failure mode is fail closed.
- [x] 6.2 Run `./gradlew test` and `./gradlew build`, then resolve only failures caused by this
  change.
- [x] 6.3 Run strict OpenSpec validation for `limit-pending-link-applications`,
  `git diff --check`, and a final scope review confirming no migration, compatibility branch,
  Console capacity UI, generated client change, or unrelated cleanup was introduced.
