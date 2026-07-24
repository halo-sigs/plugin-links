## Why

Friend-link requests are often posted as comments on a site's links page, an article, or an
independent page, but administrators currently have to notice and transcribe those requests
manually. The plugin already supports optional AI-assisted comment extraction and a
`LinkApplication` review workflow, so it can connect those capabilities to turn new comments into
traceable pending applications without bypassing administrator review.

## What Changes

- Add configurable, AI-powered recognition of newly created top-level comments on selected links,
  post, and single-page subjects.
- When AI classifies a comment as a friend-link application and a valid website URL can be
  resolved, automatically create a `PENDING` `LinkApplication`; do not create a formal `Link`
  automatically.
- Add source metadata to `LinkApplication` so form submissions, comment-recognized applications,
  historical records, source comments, model decisions, and retained comment snapshots can be
  distinguished and audited.
- Add source-aware URL deduplication so rejected AI false positives do not block later form
  submissions while explicit form rejections still prevent comment recognition from bypassing an
  administrator decision.
- Extend the Console application review UI with source badges, recognition context, source links,
  and an operational warning when automatic recognition is configured but AI Foundation is
  unavailable.
- Preserve the existing administrator-initiated "extract from comment" tool and repair the
  request and structured-output contracts required to share its AI integration safely.
- Keep AI Foundation optional, constrain its compatible version range, and leave all non-AI link
  management features operational when it is absent or disabled.

## Capabilities

### New Capabilities

- `comment-link-application-recognition`: Configurable comment-subject matching, optional AI
  Foundation classification and extraction, new-comment-only execution, failure isolation, and
  automatic creation of pending link applications.

### Modified Capabilities

- `link-application`: Add application origin and recognition audit data, source-aware duplicate
  handling, and source context in the existing administrator review workflow.

## Impact

- Backend: comment event/controller integration, AI Foundation adapter reuse, settings parsing,
  application creation and deduplication services, `LinkApplication` schema/indexes, status APIs,
  and tests.
- Frontend: AI settings schema, generated API client updates, application list/detail source
  presentation, and an operational availability warning.
- Dependencies: retain `run.halo.aifoundation:api` as `compileOnly` and narrow the optional
  `ai-foundation?` plugin dependency to the supported 1.x range.
- Existing behavior: anonymous `/links/apply` submissions and manual AI extraction remain
  available; no historical comments are scanned or replayed.
