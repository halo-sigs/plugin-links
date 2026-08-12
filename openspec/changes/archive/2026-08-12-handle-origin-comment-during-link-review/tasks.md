## 1. Backend source Comment contract

- [x] 1.1 Add focused endpoint tests for current `approved` and `hidden` values, application-scoped
  source resolution, and deleted source Comments.
- [x] 1.2 Extend `LinkApplicationOriginComment` and its endpoint mapping with live approval and hidden
  state while preserving existing 404 and authorization behavior.
- [x] 1.3 Run `./gradlew generateApiClient`, inspect the generated client diff, and verify the new
  response fields are available without hand-editing generated files.
- [x] 1.4 Document the completed backend contract, remaining frontend responsibilities, integration
  points, and verification commands for the frontend implementation handoff.

## 2. Frontend Comment operations

- [x] 2.1 Add focused unit tests for permission gating, default approval selection, hidden and
  deleted states, action summaries, and plain-text-to-safe-HTML conversion.
- [x] 2.2 Add source-Comment approval and reply mutations using Halo's existing Core and Console API
  clients, preserving current-user reply identity and normal notification behavior.
- [x] 2.3 Add state-aware refresh and reconciliation helpers that skip already-complete approval and
  refresh the latest replies without automatically retrying an indeterminate reply request.

## 3. Application review workflow

- [x] 3.1 Add the permission-aware source-Comment controls, hidden-state warning, reply input, and
  live action summary to Comment-origin application details without changing form-origin review.
- [x] 3.2 Sequence pending and resumable application approval before selected Comment actions, and
  close the modal only when the selected operations have a definite successful outcome.
- [x] 3.3 Preserve an approved Link on Comment failure, keep the modal open in approved mode, report
  partial success precisely, and expose a retry for only the still-applicable Comment action.
- [x] 3.4 Keep standalone Comment handling available on approved applications, disable it for
  rejected applications or deleted sources, and keep controls disabled with an explanation when
  `system:comments:manage` is absent.
- [x] 3.5 Present an indeterminate reply outcome without claiming failure, refresh Comment and reply
  state, and require reviewer confirmation before any manual resubmission.

## 4. Verification

- [x] 4.1 Run the focused backend and Console unit tests covering success, partial failure,
  permission loss, concurrent approval, hidden Comments, deleted Comments, and indeterminate reply
  outcomes.
- [x] 4.2 Run `pnpm type-check`, `pnpm test:unit`, and `pnpm build` in `console/`, then run the
  relevant Gradle tests and `./gradlew build` for the integrated plugin.
- [x] 4.3 Inspect the final diff to confirm `roleTemplate.yaml` does not grant Comment permissions,
  no Comment-processing snapshot was added to LinkApplication, and generated API files match the
  backend OpenAPI contract.
