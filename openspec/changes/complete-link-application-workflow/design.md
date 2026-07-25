## Context

The current PR introduces anonymous HTML form applications and AI recognition of applications from
new Comments. Both sources create `LinkApplication` Extension resources that administrators review
in Console. The main path works, but several boundaries are incomplete:

- `/links/apply` is always active and comment recognition is configured under a separate AI group.
- approval creates a `Link` before updating the application, so concurrent or interrupted requests
  can produce duplicate or contradictory state;
- approval-time verification and RSS refresh depend on a browser callback;
- application listing loads every result and Console hides terminal history;
- the application UI reads Comments directly even though delegated link managers do not have
  general Comment permission;
- named application subresource RBAC incorrectly restricts `resourceNames` to `-`;
- form creation and rate limiting contain single-process races and unbounded state.

Halo Extension writes provide resource-version conflict detection but not transactions across a
`LinkApplication` and a `Link`. The design therefore uses an explicit resumable state machine
instead of claiming cross-resource atomicity. The agreed deployment scope is one plugin instance.

## Goals / Non-Goals

**Goals:**

- Make all new application creation disabled by default and controlled from one coherent settings
  group.
- Keep form and comment channels independently configurable under one master switch.
- Make approval idempotent and recoverable across duplicate requests and partial failures.
- Move post-approval automation to backend orchestration.
- Provide paginated application history, source-aware review, and direct cleanup of the current
  filtered result.
- Make delegated link-management RBAC match the actual named and collection subresources.
- Close theme documentation, generated-client, type-check, test, and final E2E gaps.

**Non-Goals:**

- Distributed locking or duplicate guarantees across multiple Halo/plugin instances.
- A public JSON submission API.
- Replaying Comments missed while recognition or AI Foundation was unavailable.
- Historical Comment scanning, Reply recognition, automatic approval, applicant notifications,
  CAPTCHA, public application status, or website enrichment.
- Scheduled application retention or an additional cleanup-preview workflow.
- Granting link managers general access to arbitrary Halo Comments.

## Decisions

### 1. Use one Link Application settings group with a master switch

Add an `application` setting group with this logical shape:

```yaml
enabled: false
selfSubmission:
  enabled: true
commentRecognition:
  enabled: false
  modelName: null
  sources: []
```

`enabled` is the master switch. Effective form submission requires both `enabled` and
`selfSubmission.enabled`; effective comment recognition requires `enabled`,
`commentRecognition.enabled`, a selected operational model, and at least one valid source.

The recognition controls move out of the `ai` setting group and no longer depend on its master
switch. The existing AI group continues to control only administrator-initiated Comment extraction
when creating a formal Link. This avoids one feature depending on switches in two settings groups.
Because the recognition settings only exist on the unmerged feature branch, no persisted setting
migration is required.

The theme model exposes the effective form-enabled value as `linkApplicationEnabled`. When disabled,
the form endpoint performs no rate-limit or validation work and redirects to
`/links?applied=disabled&message=友链申请功能暂未开放`. Existing applications remain reviewable.

The recognition help text states that matching new top-level Comments, including hidden or
unapproved Comments, send their raw content and the already-specified minimal context to the
selected AI model.

### 2. Serialize application creation by canonical URL within one process

Introduce a shared, process-local canonical-URL coordinator around the complete
list/check/create operation used by FORM and COMMENT submissions. Entries are removed when work
finishes so the coordinator does not become an unbounded URL registry. Duplicate checks are
repeated inside the serialized section.

This closes same-instance FORM/FORM and FORM/COMMENT races. It deliberately does not claim
cross-instance safety or storage-level uniqueness. Approval still performs a fresh formal-Link and
application duplicate check immediately before reserving an approval.

The form rate limiter uses an atomic per-IP update and bounded expiry cleanup. It retains the
existing one-request-per-minute behavior and uses the server request's resolved remote address.

### 3. Model approval as a resumable state machine

Extend application status with `APPROVING` and add nested approval state:

```yaml
spec:
  status: APPROVING
  approval:
    linkName: link-...
    request:
      url: https://example.com
      displayName: Example
      logo: ...
      description: ...
      groupName: ...
```

The first approval request:

1. validates the effective URL, display name, selected group, and duplicate rules;
2. chooses a stable Link metadata name and stores the normalized approval request;
3. updates `PENDING` to `APPROVING` using the current resource version.

Only the request that wins that update creates the Link. Once reserved, approval fields are frozen.
Subsequent approve requests for `APPROVING` ignore replacement fields and resume the stored request.
If the stable Link already exists and belongs to this application, the operation continues rather
than creating another Link. An `APPROVED` retry returns the recorded Link. `REJECTED` requests
conflict.

After the Link is present, the handler records `APPROVED` and `approval.linkName`. Known validation
errors happen before reservation. Infrastructure uncertainty after reservation leaves the
application `APPROVING`, where Console exposes only "Continue approval". `APPROVING` cannot be
rejected, individually deleted, or bulk deleted.

This is a recoverable saga, not an atomic transaction. A Link records its owning application through
metadata so an unrelated Link with the reserved name is treated as a conflict.

### 4. Trigger post-approval work from the backend

After the application reaches `APPROVED`, backend orchestration submits link verification and an
initial RSS refresh using the existing services. These operations are best effort and do not roll
back the Link or application. Their existing Link status and retry controls remain the source of
truth for failures.

The frontend removes its approval-success triggers. Manual application backlink verification reuses
the existing backlink fetch and match implementation instead of maintaining a second URL-matching
algorithm in the endpoint.

### 5. Use one paginated query contract for history and cleanup

Replace the fake unpaged application `ListResult` with a real paginated query, sorted newest first.
The query supports page, size, status, origin type, and creation-time filters. Console defaults to
20 items and exposes `PENDING`, `APPROVING`, `APPROVED`, and `REJECTED` history views. The pending
card uses the server-reported total.

A collection cleanup operation receives the same filter contract and applies it again on the
server. It deletes every matching application across all pages except `APPROVING`, then returns
matched, deleted, failed, and skipped counts. There is no preview endpoint or age preset. Console
uses the current list total in its destructive confirmation.

Deleting `PENDING` or `REJECTED` applications intentionally removes their duplicate-blocking effect,
so their URLs may be submitted again. Deleting `APPROVED` applications never deletes the associated
Link. Confirmation copy explains these effects, especially when filtered results include
form-origin rejections.

### 6. Resolve source Comments through an application-scoped endpoint

Add `GET /linkapplications/{name}/origin-comment`. The backend fetches the application, verifies
that it references a Comment, and returns only the Comment name, raw content, subject reference, and
creation time. Callers cannot supply an arbitrary Comment name.

The endpoint belongs to link-application management permission, not general Comment permission.
A missing application or deleted Comment returns not found; the application remains reviewable and
Console distinguishes unavailable source content from authorization failures.

### 7. Align RBAC rules with Halo subresource parsing

Named paths such as `linkapplications/{name}/approve` are evaluated as resource
`linkapplications/approve` with the application metadata name as the resource name. The manage role
therefore grants create on approve, reject, and verify subresources without a literal `-`
`resourceNames` restriction.

The source-Comment subresource grants get to the manage role. The collection cleanup path uses
`linkapplications/-/cleanup`, so its RBAC rule retains `resourceNames: ["-"]`. Base get/list and
delete permissions continue to govern history and individual deletion.

### 8. Keep the public transport contract narrow but extensible

This change keeps `/links/apply` as same-origin, CSRF-protected,
`application/x-www-form-urlencoded`. Theme documentation defines the enable flag, hidden CSRF
field, request fields, redirect query contract, value refill behavior, and a JavaScript
`URLSearchParams` example.

The domain creation service does not depend on form transport. A future JSON API can add its own
path, authentication/CSRF decision, and response DTO while reusing settings, validation, duplicate
rules, coordination, and rate limiting.

### 9. Regenerate and validate API consumers as one change

Backend API/schema changes are followed by `generateApiClient`. Generated Halo 2.25.2 models that
correctly expose optional metadata are accepted; handwritten consumers add the required guards
instead of editing generated types to restore older assumptions. Generated output is mechanically
cleaned so the full PR passes `git diff --check`.

## Risks / Trade-offs

- [A process crash can leave an application in `APPROVING`] → The stable Link identity and stored
  request make "Continue approval" safe and idempotent.
- [Backend automation may fail after approval] → Do not roll back durable approval; expose failures
  through existing Link verification/RSS state and manual retries.
- [No cross-instance uniqueness] → Document single-instance scope and keep the state machine safe
  for concurrent requests inside that instance.
- [Bulk cleanup can allow previously blocked URLs to reapply] → State the effect in confirmation
  copy and return exact deletion/skip counts.
- [Application history can grow] → Use indexed filters and server pagination; scheduled retention
  remains a future change.
- [Raw hidden or unapproved Comment content reaches an external model] → Keep recognition
  disabled by default and disclose the data transfer next to its switch.
- [Settings fetch failure could accidentally expose submission] → Normalize all missing or failed
  application settings to master-disabled.

## Migration Plan

1. Add the application settings schema and normalized defaults before gating either creation path.
2. Add `APPROVING` and optional nested approval data; historical applications without origin or
   approval data remain readable.
3. Update backend endpoints, orchestration, RBAC, and tests.
4. Regenerate the API client, then update Console history, recovery, source details, and cleanup.
5. Add theme documentation and run the complete validation matrix.

Rollback is code-only because existing resources remain valid under the prior schema. Applications
left in `APPROVING` by a new version would require completing or manually repairing them before
running an older version that does not understand the state.

## Open Questions

None. Product decisions for switches, single-instance scope, approval recovery, cleanup semantics,
source permissions, and future JSON transport were resolved during proposal discovery.
