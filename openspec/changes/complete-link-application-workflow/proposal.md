## Why

The link application workflow now covers form and AI-comment submissions, but it is not yet safe
or complete enough to merge: application creation cannot be disabled globally, approval can create
inconsistent or duplicate Links, delegated link managers lack required permissions, and retained
applications have no paginated history or bulk-cleanup workflow. This change closes those product,
consistency, security, documentation, and validation gaps without expanding into a public JSON API
or multi-instance coordination.

## What Changes

- Add a disabled-by-default Link Application settings group with a master switch, an independently
  configurable visitor form channel, and comment-recognition settings moved from the AI group.
- Gate both form submission and comment recognition on the master switch while keeping existing
  applications reviewable when new submissions are disabled.
- Make single-instance application creation concurrency-safe by canonical URL and replace the
  unbounded, non-atomic form rate limiter with bounded atomic rate limiting.
- Add an idempotent `PENDING -> APPROVING -> APPROVED` approval workflow that freezes approval
  inputs, records the created Link identity, validates overrides server-side, and safely resumes
  interrupted approvals.
- Trigger link verification and initial RSS refresh from the backend after approval without rolling
  back an approved Link when post-approval automation fails.
- Add paginated, filterable application history, source-aware details, and server-side bulk deletion
  for the current filter across all pages, while protecting `APPROVING` applications.
- Correct RBAC for named application subresources and expose only the source Comment fields needed
  for application review.
- Document theme form integration, CSRF handling, setting exposure, redirects, and JavaScript
  form-encoded submission; keep a future JSON API out of scope.
- Bring generated clients, UI handling, OpenSpec wording, and automated validation to a clean
  merge-ready state.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `link-application`: Add global/channel settings, disabled behavior, concurrency controls,
  resumable approval, application history, source inspection, filtered bulk deletion, and
  source-aware lifecycle behavior.
- `comment-link-application-recognition`: Move recognition configuration under Link Application,
  gate processing on the application master switch, and disclose model data processing.
- `rbac-config`: Authorize delegated link managers for named application subresources without
  granting general Comment access.
- `link-verification`: Trigger verification from backend application approval and reuse the existing
  verification behavior.
- `link-rss-feed`: Trigger initial RSS refresh from backend application approval.

## Impact

- Backend: LinkApplication schema and lifecycle, settings DTO/fetchers, form route, comment
  recognition, approval and cleanup endpoints, source-context endpoint, concurrency/rate limiting,
  verification and RSS orchestration, RBAC, and tests.
- Frontend: settings schema, paginated application history and filters, approval recovery,
  source-context rendering, cleanup actions, generated API consumers, and review copy.
- Public theme contract: `/links/apply` remains form-encoded and CSRF-protected but becomes gated by
  settings and gains documented enabled/disabled template behavior.
- API/client: Console application list, approval, source-context, and cleanup contracts change and
  require regenerating the TypeScript client.
- Deployment: correctness is guaranteed for concurrent work within one plugin instance; distributed
  multi-instance coordination and a JSON submission API remain out of scope.
