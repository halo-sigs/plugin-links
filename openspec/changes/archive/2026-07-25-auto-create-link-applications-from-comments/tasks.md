## 1. Stabilize the Existing AI Baseline

- [x] 1.1 Fix the stale `LinkRouterTest` construction and add focused test fixtures for comments, subjects, AI availability, and LinkApplication resources.
- [x] 1.2 Replace the manual comment-extraction record-derived schema with an explicit schema whose extraction fields are optional, and cover Map results, omitted values, and malformed output.
- [x] 1.3 Return HTTP 400 for a missing or blank manual extraction request and verify the existing extraction success contract remains unchanged.
- [x] 1.4 Add a classpath-isolation test proving the plugin starts and omits AI-dependent components when AI Foundation classes are absent.

## 2. Add Settings and Application Origin Data

- [x] 2.1 Add the disabled-by-default automatic-recognition settings, structured-output model selector, and Repeater source rules for links, post, and single-page subjects.
- [x] 2.2 Add conditional settings validation for post and single-page selections and normalize duplicate or incomplete source rules at runtime.
- [x] 2.3 Extend `LinkApplication` with optional `FORM`/`COMMENT` origin metadata while preserving records without origin.
- [x] 2.4 Register indexes for source-aware duplicate checks and stable comment-name idempotency.
- [x] 2.5 Regenerate the OpenAPI TypeScript client and verify the generated schema represents the origin and recognition settings/status contracts.

## 3. Centralize LinkApplication Creation

- [x] 3.1 Implement conservative HTTP(S) URL normalization and canonical comparison without collapsing schemes or non-root paths and queries.
- [x] 3.2 Extract a shared reactive LinkApplication creation service that validates input and returns explicit created, duplicate, or invalid outcomes.
- [x] 3.3 Implement the formal-Link, active-application, rejected-form, rejected-comment, and comment-name duplicate matrix in the shared service.
- [x] 3.4 Refactor `/links/apply` to use the shared service, record `FORM` origin, and preserve existing redirects, validation, and rate limiting.
- [x] 3.5 Add backend tests covering URL canonicalization, every duplicate-matrix branch, historical applications without origin, and repeated/concurrent comment delivery.

## 4. Build the Conditional AI Recognition Service

- [x] 4.1 Extract model resolution and structured-output execution behind a conditional internal service whose always-loaded API exposes only plugin-owned types.
- [x] 4.2 Keep separate manual-extraction and automatic-recognition prompts, models, schemas, and result contracts while reusing model invocation infrastructure.
- [x] 4.3 Implement the fixed versioned recognition prompt with untrusted-comment framing and the minimum allowed subject and owner context.
- [x] 4.4 Parse recognition output with only `isLinkApplication` required, then apply URL, display-name, optional-field, and no-web-enrichment business rules.
- [x] 4.5 Bound automatic model execution to a 30-second total timeout and at most two retries, and emit structured diagnostics after terminal failure.
- [x] 4.6 Extend AI status reporting to distinguish class availability from an enabled `AiModelService`, and constrain the optional dependency to `>=1.0.0-beta.4 & <2.0.0`.
- [x] 4.7 Add tests for positive and negative classification, owner fallbacks, privacy exclusions, prompt injection content, timeout/retry behavior, model unavailability, and invalid structured output.

## 5. Process New Comment Events

- [x] 5.1 Add a managed single-worker top-level `Comment` controller that disables startup synchronization and ignores update, delete, and `Reply` events.
- [x] 5.2 Match new comments against normalized links-page, post, and single-page source rules, including subject-title lookup for model context.
- [x] 5.3 Gate processing on all required AI and recognition settings without preventing comment creation or other plugin behavior.
- [x] 5.4 Invoke recognition, resolve field fallbacks, copy email only after a positive decision, and create a `COMMENT`-origin `PENDING` application through the shared service.
- [x] 5.5 Persist the source Comment name as `origin.comment.name` while never creating a formal Link.
- [x] 5.6 Add controller tests for every source type, duplicate rules, owner/status variants, ignored lifecycle events, missed unavailable-period comments, and bounded failures.

## 6. Extend the Console Review Flow

- [x] 6.1 Fix the generated-client request-property use in the existing manual extraction flow and add a focused frontend test for successful form prefill.
- [x] 6.2 Show form, comment, and historical source labels in the existing pending-application list.
- [x] 6.3 Load and show the current comment subject, management link, and raw content in application details, including a safe fallback when the original comment is unavailable.
- [x] 6.4 Display a non-blocking warning when recognition is configured but the AI integration is not operational.
- [x] 6.5 Add focused Vue tests for source presentation, comment references, historical records, and AI status warning states.

## 7. Verify the Complete Change

- [x] 7.1 Run backend tests and verify optional-AI classpath isolation, controller lifecycle semantics, duplicate handling, and manual extraction compatibility.
- [x] 7.2 Run targeted frontend tests, formatting, and type checking; fix failures in touched files and document unrelated pre-existing diagnostics separately.
- [x] 7.3 Regenerate the API client from the final backend schema and verify no hand-written duplicate API wrapper or stale generated diff remains.
- [x] 7.4 Run the full Gradle build and `git diff --check`.
- [x] 7.5 Smoke-test in Halo with AI Foundation absent and present, confirming configured new comments create only reviewable pending applications with source references and missed or historical comments are not replayed.

## 8. Simplify Comment Origin Data

- [x] 8.1 Replace the flat comment recognition audit fields with a `comment.name` source reference while keeping `FORM` and `COMMENT` as stable origin types.
- [x] 8.2 Update duplicate detection, recognition creation, indexes, and backend tests for the nested comment reference.
- [x] 8.3 Resolve current comment subject and content on demand in the Console, regenerate the API client, and update focused frontend tests.
- [x] 8.4 Update the change artifacts and rerun backend, frontend, OpenSpec, build, and diff validation.
- [x] 8.5 Accept explicit null values for optional automatic-recognition fields and cover the real-model structured-output regression.
