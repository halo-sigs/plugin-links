## 1. Backend Model and API Contract

- [x] 1.1 Extend `Link.spec` with optional `verification.backlinkScanUrl`.
- [x] 1.2 Extend `Link.status` with `verification.access` and `verification.backlink` result objects.
- [x] 1.3 Add verification request/result DTOs for selected-link, group, and all-link trigger scopes.
- [x] 1.4 Add a Console endpoint route for `POST links/-/verification/check` with OpenAPI metadata.
- [x] 1.5 Resolve trigger scope in the backend: names first, group name second, all links when neither is provided.

## 2. Verification Service

- [x] 2.1 Add a link verification service with an in-memory running set and bounded asynchronous execution.
- [x] 2.2 Add lightweight, SSRF-protected reachability fetching for `spec.url` with redirect validation and response bounds.
- [x] 2.3 Add SSRF-protected backlink scan fetching for `spec.verification.backlinkScanUrl`.
- [x] 2.4 Implement HTML anchor parsing and normalized matching against Halo's configured external URL.
- [x] 2.5 Update each requested `Link.status.verification` with per-link access and backlink results.
- [x] 2.6 Ensure verification is not wired to `LinkReconciler`, plugin startup, or a scheduled job.

## 3. Backend Tests

- [x] 3.1 Add service tests for accessible, inaccessible, backlink found, backlink missing, unconfigured backlink, and missing external URL cases.
- [x] 3.2 Add service tests for duplicate running-link de-duplication and per-link failure isolation.
- [x] 3.3 Add endpoint tests for selected-link, group, all-link, and unknown-link trigger scopes.
- [x] 3.4 Add SSRF-focused tests that private, reserved, non-HTTP, oversized, and unsafe redirect targets are rejected during verification.

## 4. Frontend Integration

- [x] 4.1 Run `./gradlew generateApiClient` so the generated Console client includes the verification endpoint and new Link fields.
- [x] 4.2 Update `LinkForm` and `LinkFormState` to edit the optional backlink scan URL.
- [x] 4.3 Trigger single-link verification after successful Console create and edit without blocking modal close or success feedback.
- [x] 4.4 Add Console actions for checking all links, one group, and selected links through the generated API client.
- [x] 4.5 Short-poll or invalidate link-list queries while visible links have `CHECKING` verification status.
- [x] 4.6 Update `LinkBadge` to display reachability and backlink status indicators with useful tooltips.

## 5. Validation

- [x] 5.1 Run `./gradlew test`.
- [x] 5.2 Run `pnpm --dir console type-check`.
- [x] 5.3 Run `pnpm --dir console test:unit` for frontend unit coverage added during implementation.
- [x] 5.4 Run `openspec validate add-link-verification --strict`.
- [x] 5.5 Run `git diff --check`.
- [x] 5.6 Smoke-test the Console create/edit and manual verification flows in the Halo dev environment when frontend behavior changes are implemented.
