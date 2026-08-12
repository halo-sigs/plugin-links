# Frontend implementation handoff

## Scope and current status

The backend portion of `handle-origin-comment-during-link-review` is complete. OpenSpec tasks
1.1–1.4 are checked.

The frontend implementation described below has since been delivered and verified: tasks 2.x, 3.x,
and the cross-stack verification tasks 4.x are checked in `tasks.md`. This document is kept as the
record of the backend contract and the integration points the frontend was built against.

`roleTemplate.yaml` was intentionally not changed.

## Delivered backend contract

`GET /apis/console.api.link.halo.run/v1alpha1/linkapplications/{name}/origin-comment` now includes
two required booleans:

```json
{
  "name": "comment-a",
  "raw": "source comment",
  "subjectRef": {},
  "creationTime": "2026-07-25T00:00:00Z",
  "approved": false,
  "hidden": true,
  "subject": null
}
```

Contract details:

- `approved` and `hidden` are required in OpenAPI and non-optional in the generated TypeScript
  model.
- Nullable values from Halo's Java model are normalized to `false`, matching Halo's field defaults.
- The Comment name remains server-derived from `LinkApplication.spec.origin.comment.name`; callers
  cannot select another Comment.
- A deleted source Comment still returns 404. Link application approval remains independently
  available.
- No Comment status is copied into LinkApplication, so every origin-Comment query returns current
  state.

Relevant delivered files:

- `src/main/java/run/halo/links/dto/LinkApplicationOriginComment.java`
- `src/main/java/run/halo/links/endpoint/LinkApplicationEndpoint.java`
- `src/test/java/run/halo/links/endpoint/LinkApplicationEndpointTest.java`
- `api-docs/openapi/v3_0/linksV1alpha1Api.json`
- `console/src/api/generated/models/link-application-origin-comment.ts`

## Required Halo APIs

Use the clients exported by `@halo-dev/api-client`; do not add duplicate plugin endpoints.

Approve an unapproved source Comment:

```ts
await coreApiClient.content.comment.patchComment({
  name: commentName,
  jsonPatchInner: [
    { op: "add", path: "/spec/approved", value: true },
    { op: "add", path: "/spec/approvedTime", value: new Date().toISOString() },
  ],
});
```

Create a reply:

```ts
await consoleApiClient.content.comment.createReply({
  name: commentName,
  replyRequest: {
    raw: replyText,
    content: safeReplyHtml,
    allowNotification: true,
    quoteReply: undefined,
  },
});
```

Do not pass `owner`. Halo assigns the current authenticated reviewer. Halo's Console reply service
also approves the parent Comment, so when a non-blank reply is submitted, do not send a preceding
Comment approval patch.

For an indeterminate reply result, the existing API can reload recent replies:

```ts
await consoleApiClient.content.reply.listReplies({
  commentName,
  page: 1,
  size: 20,
  sort: ["metadata.creationTimestamp,desc"],
});
```

Never automatically resubmit a reply after a timeout or response loss.

## Permission boundary

Comment actions require the independent Halo UI permission:

```ts
const canManageComments = computed(() =>
  utils.permission.has(["system:comments:manage"]),
);
```

The plugin's `plugin:links:manage` role must not gain Comment or Reply API rules. When
`canManageComments` is false, keep the source preview and “打开评论管理” link, render the new
controls disabled, and explain the missing permission. A 403 after the modal opens is a
Comment-only failure; it must not change or misreport successful link approval.

## Recommended component and state boundaries

Keep `LinkApplicationDetailModal.vue` as the orchestration owner because it already controls link
approval and modal closure. It should own the selected Comment intent and enforce this order:

1. Await pending or resumable link approval.
2. Only after it succeeds, run the selected Comment operation.
3. Close only after every selected operation has a definite success result.
4. On Comment failure, retain an explicit local `link-approved` phase (or refetch the application),
   keep the modal open, and expose only the still-applicable Comment action.

Do not mutate the `application` prop to fake the approved state.

Keep the source preview focused. A small child such as
`LinkApplicationOriginCommentActions.vue` can receive current origin state and permission as typed
props, expose reply text through `defineModel`, and emit a typed submit intent. Keep HTTP side
effects in a focused composable, while pure action-summary and plain-text escaping functions remain
ordinary utilities. This follows the existing Composition API and props-down/events-up structure
without turning `LinkApplicationOriginDetails.vue` into a second orchestrator.

Export the existing origin-Comment query key from `use-link-application.ts` so mutations can
invalidate it consistently. Derive visibility, default selection, warning text, and action summary
with `computed`; use watchers only to reset local input when the application identity changes.

## Required behavior checklist

- Only `COMMENT` origin applications show Comment handling.
- `PENDING` and `APPROVING`: link approval must finish before Comment handling begins.
- Unapproved Comment: approval defaults selected.
- Approved Comment: do not show or send a redundant approval patch; an optional reply remains.
- Non-blank reply: safely escape plain text to HTML, create one reply, and rely on Halo to approve
  the parent Comment.
- Blank reply: do not send a reply; an approval patch may still run when selected.
- Hidden Comment: show a warning and never modify `hidden`.
- Deleted Comment: disable Comment controls without disabling link approval.
- `APPROVED` application: expose a separate Comment-processing button for recovery.
- `REJECTED` or form-origin application: expose no Comment mutation actions.
- Determinate Comment failure after link success: keep the modal open and say exactly that the Link
  succeeded while Comment handling failed.
- Indeterminate reply: do not call the reply endpoint again automatically; reload current Comment
  and reply state and require reviewer confirmation before manual resubmission.
- Keep normal request failures under Halo's global Axios interceptor; use inline partial-success or
  indeterminate state instead of adding a duplicate generic failure toast.

## Suggested focused tests

Place new tests alongside the existing feature tests under `console/src/utils/__tests__/` and
component `__tests__/` directories as appropriate. Cover at least:

- permission granted and denied;
- unapproved default selection and approved no-op;
- hidden and deleted source states;
- form-origin and rejected exclusions;
- link failure prevents every Comment request;
- link success precedes Comment approval or reply;
- partial success keeps the modal open and permits Comment-only retry;
- reply path does not also patch approval;
- HTML-significant plain text is escaped and newlines are preserved safely;
- network-ambiguous reply is not automatically retried.

## Verification commands

Backend verification already completed:

```bash
./gradlew test --tests run.halo.links.endpoint.LinkApplicationEndpointTest
./gradlew generateApiClient
```

After frontend implementation, run:

```bash
cd console
pnpm type-check
pnpm test:unit
pnpm build

cd ..
./gradlew test
./gradlew build
openspec validate handle-origin-comment-during-link-review --strict --json
git diff --check
```

Do not rerun API generation unless the backend contract changes again. The generator currently
produces unrelated whitespace and stale-contract drift in several existing generated files; those
unrelated changes were deliberately removed from this handoff.
