## 1. Application Contract and Settings

- [x] 1.1 Extend `LinkApplication` with `APPROVING` and nested approval request/link identity data,
  preserving compatibility with historical resources
- [x] 1.2 Add or update LinkApplication indexes needed for status, origin, creation-time filtering,
  and newest-first pagination
- [x] 1.3 Add normalized `application` settings with a disabled master switch, enabled visitor child
  switch, and disabled Comment-recognition child settings
- [x] 1.4 Move Comment-recognition model and source controls from the AI settings group into the
  Link Application group, including the raw-Comment privacy disclosure
- [x] 1.5 Update settings and AI-status tests for effective master/channel behavior, safe defaults,
  unavailable AI Foundation, and settings-fetch failure

## 2. Submission Creation and Rate Limiting

- [x] 2.1 Gate `/links/apply` before rate limiting or validation, expose
  `linkApplicationEnabled` to the theme, and implement the disabled redirect contract
- [x] 2.2 Gate Comment recognition on the application master and recognition child settings without
  depending on the AI group master switch
- [x] 2.3 Implement a process-local canonical-URL coordinator shared by FORM and COMMENT creation,
  with coordination entries released after completion
- [x] 2.4 Re-run formal-Link and source-aware application duplicate checks inside the coordinated
  create operation
- [x] 2.5 Replace the form rate limiter with atomic per-IP admission and bounded expiration cleanup
- [x] 2.6 Add tests for disabled channels, concurrent FORM/FORM and FORM/COMMENT creation, duplicate
  outcomes, coordinator cleanup, and concurrent rate-limit admission

## 3. Idempotent Approval and Backend Automation

- [x] 3.1 Add backend validation for effective approval fields, selected group, canonical URL, and
  formal/application duplicates before reserving approval
- [x] 3.2 Implement the resource-version-guarded `PENDING -> APPROVING` reservation that freezes the
  normalized approval request and stable Link name
- [x] 3.3 Create or recover the application-owned Link idempotently and complete
  `APPROVING -> APPROVED` with `approval.linkName`
- [x] 3.4 Make repeated `APPROVING` and `APPROVED` requests resume or return the recorded Link, and
  reject conflicting lifecycle operations
- [x] 3.5 Move verification and initial RSS refresh triggers into backend post-approval orchestration
  without rolling back durable approval when either trigger fails
- [x] 3.6 Reuse the existing backlink fetch and match implementation for manual application
  verification instead of maintaining endpoint-local URL matching
- [x] 3.7 Add endpoint/service tests for approval validation, concurrent approve, approve/reject
  competition, interruption at each durable boundary, idempotent retry, Link ownership conflicts,
  and post-approval automation failure

## 4. History, Source Context, Cleanup, and RBAC

- [x] 4.1 Replace the unpaged application list with real page/size/total results and server filters
  for status, origin type, and creation time
- [x] 4.2 Add the application-scoped origin-Comment endpoint returning only name, raw content,
  subject reference, and creation time
- [x] 4.3 Add server-side cleanup using the current list filters across all pages, deleting
  PENDING/APPROVED/REJECTED records, skipping APPROVING, and returning matched/deleted/failed/skipped
  counts
- [x] 4.4 Reject individual deletion of APPROVING while preserving deletion semantics for all other
  statuses
- [x] 4.5 Correct named approve/reject/verify RBAC, add scoped origin-Comment permission, and add the
  `-` collection cleanup permission without granting general Comment access
- [x] 4.6 Add query, source-deletion, cleanup-partial-failure, lifecycle-protection, delegated-role,
  unauthorized-role, and named/collection subresource tests

## 5. Generated Client and Console Workflow

- [x] 5.1 Regenerate the OpenAPI document and TypeScript client after backend schema and endpoint
  changes
- [x] 5.2 Update handwritten consumers for Halo 2.25.2 optional metadata and clean generated changes
  so type checking and full-PR whitespace checks pass
- [x] 5.3 Replace the pending-only modal with paginated application history and status, source, and
  creation-time filters while keeping the pending total card
- [x] 5.4 Add APPROVING presentation and a continue-approval action that cannot edit the frozen
  request
- [x] 5.5 Use the scoped origin-Comment endpoint and distinguish deleted source content from
  authorization failures
- [x] 5.6 Remove frontend post-approval verification/RSS triggers and make rejection confirmation
  source-aware
- [x] 5.7 Add direct cleanup for all records matching the current filter, including total-based
  confirmation, resubmission warnings, and deleted/failed/skipped result feedback
- [x] 5.8 Add composable and component tests for pagination/filter propagation, history states,
  approval resume, source states, cleanup semantics, and source-aware copy

## 6. Theme and Specification Documentation

- [x] 6.1 Document the Link Application settings, `linkApplicationEnabled` template value, form
  fields, CSRF hidden input, redirect/error query parameters, and value-refill behavior
- [x] 6.2 Add both HTML and same-origin JavaScript `URLSearchParams` examples for the form-encoded
  `/links/apply` contract and state that a JSON submission API is not included
- [x] 6.3 Replace the archived `link-application` specification's placeholder Purpose with an
  accurate description when integrating this delta
- [ ] 6.4 Update PR-facing summary, scope, limitations, and validation evidence to cover settings,
  Comment recognition, approval recovery, history, cleanup, and single-instance scope

## 7. Merge-Gate Validation

- [x] 7.1 Run targeted backend tests for settings, recognition, creation coordination, rate limiting,
  approval, history, cleanup, source context, RBAC, verification, and RSS orchestration
- [x] 7.2 Run frontend unit tests, `pnpm type-check`, and formatting checks
- [ ] 7.3 Run `./gradlew build`, `git diff --check origin/main...HEAD`, and strict OpenSpec validation
- [ ] 7.4 Run a real-browser E2E for disabled FORM, enabled CSRF-protected FORM, history review,
  approval and recovery, backend automation, rejection, and filtered cleanup
- [ ] 7.5 Run real-model E2E with the application master switch disabled and enabled, including
  matching/non-matching sources, hidden or unapproved Comment disclosure assumptions, and no
  historical replay
- [ ] 7.6 Verify the final pushed PR head receives fresh remote CI instead of relying on checks from
  an earlier commit
