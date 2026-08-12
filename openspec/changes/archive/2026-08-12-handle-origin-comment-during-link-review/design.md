## Context

Comment recognition records the source Comment metadata name in
`LinkApplication.spec.origin.comment.name`. The application detail endpoint already resolves that
reference through an application-scoped `origin-comment` operation, so link managers can inspect
the source without gaining access to arbitrary Comments. The response does not currently expose
whether the Comment is approved or hidden, and the Console only links to the separate Comment
management screen.

Halo 2.25.2 already provides the two required mutations. The Core Comment API can patch
`spec.approved` and `spec.approvedTime`; the Console reply API creates an approved reply, assigns the
current authenticated user as its owner, follows Halo's normal notification flow, and approves the
parent Comment. These APIs require Halo's Comment-management permission, which is intentionally
separate from plugin-links' link-management permission.

Link approval is a resumable `PENDING -> APPROVING -> APPROVED` lifecycle. It can validate or fail
before completion, while Comment approval and reply creation are separate writes. There is no
transaction spanning these resources, and reply creation is non-idempotent.

## Goals / Non-Goals

**Goals:**

- Let an authorized reviewer approve the source Comment or reply to it without leaving link
  application review.
- Make link approval authoritative: Comment mutations occur only after link approval completes.
- Preserve accurate, current Comment state and keep recovery actions available on approved
  applications.
- Expose partial success and indeterminate reply results without rolling back a valid Link or
  creating duplicate replies automatically.
- Preserve Halo's existing Comment permissions, identity, visibility, and notification semantics.

**Non-Goals:**

- Handling Comments when rejecting a link application.
- Unhiding Comments, adding reply templates, or embedding Halo's internal rich-text Comment editor.
- Granting Comment-management authority through the link-management role.
- Persisting a duplicated Comment-processing status in LinkApplication.
- Providing a cross-resource transaction or a new combined mutation endpoint.

## Decisions

### Extend the application-scoped source response with live state

`LinkApplicationOriginComment` will add the source Comment's current `approved` and `hidden`
values. The endpoint will continue to derive the Comment name exclusively from the requested
LinkApplication and will retain its existing not-found behavior when the source is gone. The
generated TypeScript client will be regenerated after this DTO change.

This keeps source lookup constrained to the application and avoids a second arbitrary-Comment read
for rendering. Storing these values in LinkApplication was rejected because subsequent Comment
moderation would make that snapshot stale.

### Orchestrate existing Halo mutations in the Console

The Console will use `coreApiClient.content.comment.patchComment` to add `spec.approved=true` and
`spec.approvedTime`, and `consoleApiClient.content.comment.createReply` to submit a reply. It will
not pass an owner, so Halo attributes the reply to the current reviewer. Reply text will be entered
as plain text and converted to escaped HTML before it is sent as `content`; the original text is
sent as `raw`.

A new plugin backend mutation was rejected because it would either need to impersonate the current
user across Halo services or duplicate existing endpoints and authorization. A combined endpoint
would still not provide a real transaction.

### Keep link approval first and model partial success explicitly

For `PENDING` and `APPROVING` applications, the frontend waits for the link approval request to
return successfully before starting any selected Comment action. A failed or incomplete link
approval leaves the Comment untouched. Once the Link is approved, a Comment failure does not undo
the Link or change the application back to a previous state.

On partial success, the detail modal stays open in approved mode, reports that the Link succeeded
and the Comment failed, refreshes the application and origin-Comment queries, and exposes a
standalone Comment retry. The same standalone actions are available when an already `APPROVED`
Comment-origin application is opened later. Rejected applications never expose these actions.

### Derive controls from current state and explicit permission

Only users with `system:comments:manage` can invoke Comment mutations. Reviewers without that
permission see disabled controls and an explanation while retaining the source preview and Comment
management link. The link-management RoleTemplate will not depend on or aggregate the Halo
Comment-management role.

An unapproved source defaults to “also approve the source Comment.” An approved source omits that
choice but still allows a reply. A hidden source shows a warning, and neither approval nor reply
changes `hidden`. A missing source disables Comment actions without blocking link approval.

The form displays a live summary of the selected link and Comment actions instead of adding a
second confirmation dialog. On approved applications, Comment processing uses a separate action
button.

### Never automatically retry reply creation

Ordinary HTTP validation, authorization, conflict, and not-found responses are reported as Comment
failures. When a timeout or network interruption makes reply creation indeterminate, the Console
does not submit the reply again. It refreshes the source Comment and latest replies and directs the
reviewer to confirm the outcome before any manual resubmission. The UI must not claim that the
reply failed merely because the response was lost.

Comment approval is state-aware: refreshed `approved=true` is treated as already complete rather
than patched again.

## Risks / Trade-offs

- **[Two writes can partially succeed]** -> Run link approval first, retain the approved Link, keep
  the modal open, and expose a focused Comment retry with precise outcome text.
- **[Reply response can be lost after persistence]** -> Never retry automatically; refresh current
  state and latest replies for reviewer confirmation.
- **[Permission can change while the modal is open]** -> Treat a resulting 403 as a Comment-only
  failure and refresh permission-dependent state without misreporting link approval.
- **[The source Comment can change or disappear concurrently]** -> Refresh after mutations, handle
  404 and conflicts explicitly, and never block or roll back a successful link approval.
- **[Plain text has less formatting power]** -> Prefer a small stable input for short review replies
  and safely escape it instead of coupling the plugin to Halo Console internals.

## Migration Plan

No persisted extension schema changes or data migration are required. Deploy the backend DTO and
regenerated frontend client together with the new Console behavior. Rollback only removes the new
response fields and controls; existing LinkApplications and Comments remain valid.

## Open Questions

None. Product behavior, permission boundaries, failure ordering, and recovery semantics were
confirmed during exploration.
