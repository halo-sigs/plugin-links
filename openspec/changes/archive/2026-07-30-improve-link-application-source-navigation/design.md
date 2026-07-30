## Context

The application-scoped `origin-comment` operation currently returns the original Comment's raw
content, subject reference, and creation time. Console code then interprets known subject refs
itself, displays the subject resource name, and routes Post and SinglePage subjects to their
editors. This duplicates Halo subject-resolution behavior and sends reviewers away from the public
page where the application comment was submitted.

Halo already exposes `CommentSubject` as a backend extension point. Its
`getSubjectDisplay(name)` contract returns a title, public-facing URL, and kind name. Halo Core
implements it for Post and SinglePage, while plugin-links implements it for the Links page.

The origin-Comment operation remains application-scoped: callers choose only a LinkApplication, and
the backend reads the Comment name recorded in that application's origin. Link managers do not gain
an arbitrary Comment lookup operation.

## Goals / Non-Goals

**Goals:**

- Resolve Comment subjects through Halo's existing `CommentSubject` extension point.
- Return an optional, stable plugin-owned subject display DTO from `origin-comment`.
- Link reviewers to the resolved public page in a new browser tab.
- Keep Comment content useful when its subject cannot be resolved.
- Remove visible resource metadata names from link-application Console views.

**Non-Goals:**

- Add a Comment detail deep link to Halo Core.
- Add subject-type-specific Post, SinglePage, or plugin queries.
- Determine whether a resolved subject URL is currently public or reachable.
- Change application-origin persistence or allow callers to select a different Comment.
- Remove technical resource names from API payloads or internal identifiers.

## Decisions

### Enrich the existing application-scoped response

`LinkApplicationOriginComment` will keep `name`, `raw`, `subjectRef`, and `creationTime` and add an
optional plugin-owned `subject` object containing `title`, `url`, and `kindName`.

Keeping `subjectRef` preserves context for backend processing, generated clients, and diagnostics,
but Console must not render its `name`. A nested DTO keeps the additive API change explicit and
avoids exposing Halo's internal `CommentSubject.SubjectDisplay` record as this plugin's OpenAPI
contract.

Alternatives considered:

- A separate subject endpoint would add another request and duplicate application scoping.
- Returning the full subject Extension would expose unnecessary data and force frontend
  type-specific interpretation.

### Resolve through `CommentSubject`

The endpoint will inject `ExtensionGetter`, find the first registered `CommentSubject` whose
`supports(subjectRef)` returns true, and reactively call `getSubjectDisplay(subjectRef.name)`.
The result will be mapped into the plugin-owned subject DTO.

No GVK switch or direct Post/SinglePage fetch will be added. This covers Halo Core subjects,
plugin-links' own Links subject, and third-party providers through the same contract.

If no provider matches, the provider returns an empty display, or the referenced subject no longer
exists, the endpoint will return the Comment response with `subject: null`. It must not let an empty
subject-resolution publisher suppress the response.

Alternatives considered:

- Hard-coding Post and SinglePage queries would duplicate Core logic and exclude plugin-defined
  comment subjects.
- Resolving subjects in Console would require additional permissions and API calls and would keep
  navigation rules split across clients.

### Render only resolved display values

Console will replace the subject-ref-to-editor helper with rendering based on the optional
`subject` response:

- When present, the visible link text is `kindName · title`, with blank values falling back to
  `kindName` and then the generic text `评论来源`.
- The entire source label, including its external-link affordance, is a normal anchor that opens
  `subject.url` in a new tab with `rel="noopener noreferrer"`.
- When absent, Console displays `来源页面不可用` without rendering any part of `subjectRef`.
- The existing `打开评论管理` entry remains a general Comments route, but the Comment resource name
  beside it is removed.

Resource metadata names remain available internally for Vue keys, API commands, query caches, and
backend resolution. They are prohibited only as user-visible text in link-application Console
views.

### Preserve provider URL semantics

The endpoint will return the URL supplied by `CommentSubject`. Core Post and SinglePage providers
already pass permalinks through Halo's external-link processor, while plugin subjects may return
relative URLs such as `/links`.

plugin-links will not add publication, privacy, deletion, or reachability checks for individual
subject types. A provider may therefore return a URL that later responds with not found or requires
different access. This preserves extension-point semantics and avoids type-specific policy in the
plugin.

## Risks / Trade-offs

- [Risk] A resolved draft, private, recycled, or later-unpublished subject URL may not be publicly
  reachable. → Reuse the provider result without claiming availability; open it as an external
  navigation and keep the subject-unavailable fallback for unresolved displays.
- [Risk] A third-party `CommentSubject` may implement `get()` but leave `getSubjectDisplay()` empty.
  → Return the Comment with `subject: null` instead of failing the whole origin response.
- [Risk] The additive DTO change can drift from the generated TypeScript client. → Regenerate the
  OpenAPI client and cover the new response shape in backend and frontend tests.
- [Trade-off] The Comment-management link cannot identify the exact original Comment. → Keep the
  current general Comments route and leave a Halo Core deep-link design out of this change.

## Migration Plan

This is an additive response change with no stored-data migration. Deploy the backend response and
generated client together with the Console rendering update. Rollback consists of reverting the
endpoint, DTO, generated client, and Console changes; persisted LinkApplication and Comment data are
unchanged.

## Open Questions

None. Navigation, fallback, visibility, API shape, and delivery scope were settled during
exploration.
