## Why

Currently, links can only be added by administrators through the Console UI. Theme users who want to apply for a friend link must contact the site owner manually (e.g., via email or comment). This friction reduces engagement and creates extra work for administrators. A self-service link application feature allows visitors to submit link requests directly from the theme's `/links` page, streamlining the workflow for both applicants and administrators.

## What Changes

- **New Extension Model**: Introduce `LinkApplication` (GVK: `core.halo.run/v1alpha1`, kind: `LinkApplication`) to store pending link submissions. Fields: `url`, `displayName`, `logo`, `description`, `email` (optional), `backlink` (optional), `feedUrls` (optional list), `status` (`PENDING` / `APPROVED` / `REJECTED`).
- **Theme Submission Endpoint**: Add `POST /links/apply` to `LinkRouter` (WebFlux functional route), accepting `application/x-www-form-urlencoded` from HTML forms in theme templates. Supports form value回填 on validation errors via query params.
- **Rate Limiting**: In-memory IP-based rate limiter (1 request per minute per IP) on the submission endpoint to prevent abuse.
- **Duplicate Prevention**: Reject submissions if a `LinkApplication` with the same `url` already exists in `PENDING` or `REJECTED` status.
- **Console Approval UI**: Add a pending-applications alert card to the top of `LinkList.vue`. Clicking opens a modal listing pending applications. Each application opens a detail drawer/modal where administrators can:
  - Edit all fields before approving
  - Select a `LinkGroup` from a dropdown to assign on approval
  - Manually trigger backlink verification (reusing existing `VerificationSpec` logic)
  - Approve (creates a `Link`, marks application `APPROVED`) or reject (marks `REJECTED`)
- **Post-Approval Automation**: After approval, automatically trigger existing link detail fetching and RSS feed refresh (reusing `startLinkVerification` and `startInitialLinkFeedRefresh` composables).
- **Admin Deletion**: Administrators can manually delete `LinkApplication` records in any status.
- **RBAC**: Update `roleTemplate.yaml` to allow anonymous `create` on `LinkApplication` (via custom endpoint, not extension REST API). Console APIs require `link-manage` role.

## Capabilities

### New Capabilities

- `link-application`: Self-service link submission and approval workflow. Covers the `LinkApplication` extension model, theme-facing submission endpoint, rate limiting, duplicate detection, and Console approval UI.

### Modified Capabilities

<!-- No existing spec-level requirements are changing. The new feature is purely additive. -->

## Impact

- **Backend**: New Java files (`LinkApplication.java`, `LinkApplicationEndpoint.java`, rate limiter), modifications to `LinkRouter.java`, `roleTemplate.yaml`.
- **Frontend**: New Vue components (application list modal, application detail drawer/modal), modifications to `LinkList.vue`, new composables.
- **API**: New custom endpoints for submission (`/links/apply`), approval, rejection, and verification.
- **Theme**: Theme developers can add `<form action="/links/apply" method="post">` to `links.html`. No breaking changes to existing `linkFinder` API.
