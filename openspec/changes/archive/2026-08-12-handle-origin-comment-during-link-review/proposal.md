## Why

Comment recognition can create a link application from a Comment that is still unapproved or
hidden. After reviewing the link application, an administrator currently has to leave the link
workflow and find the source Comment again to approve it or reply, which makes it easy to leave the
applicant without a visible or acknowledged response.

## What Changes

- Add optional source-Comment actions to Comment-origin link application review: approve the
  Comment, or reply and let Halo approve it through the normal reply flow.
- Keep link approval authoritative and execute Comment actions only after link approval succeeds.
- Keep Comment handling available as a recovery action on already approved applications.
- Expose the source Comment's current approval and hidden state through the existing
  application-scoped origin-Comment response.
- Preserve the Comment's hidden state and keep link review available when the source Comment has
  been deleted.
- Require Halo's existing Comment-management permission for Comment mutations without expanding
  the link-management role.
- Report partial success explicitly and never automatically retry an indeterminate reply request.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `link-application`: Extend Comment-origin application review and recovery with permission-aware
  source-Comment approval and reply actions.
- `rbac-config`: Preserve the separation between link-management and Comment-management authority
  for the new actions.

## Impact

- Backend: extend the application-scoped origin-Comment DTO and OpenAPI schema with current Comment
  state.
- Frontend: update the link application detail workflow, source-Comment presentation, mutations,
  permission handling, and focused tests.
- API client: regenerate `console/src/api/generated/` after the backend response changes.
- Halo integration: reuse the existing Core Comment patch API and Console reply API; no new external
  dependency or cross-resource transaction is introduced.
