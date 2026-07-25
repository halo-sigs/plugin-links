## Context

The plugin now has two independent pieces that stop short of an automatic intake pipeline:

- `LinkApplication` stores anonymous form submissions and feeds an administrator review flow.
- The optional AI Foundation integration lets an administrator choose or paste a comment, extract
  link fields, and prefill the form for creating a formal `Link`.

The existing AI integration is intentionally optional and isolates AI Foundation types behind a
Spring condition. The new feature must preserve that property, must not scan historical comments,
and must not allow model output to bypass the existing application review boundary.

The current form handler owns application validation and duplicate checks as private methods, while
the current AI endpoint owns model resolution and structured-output conversion. Both responsibilities
need reusable internal boundaries before a background comment consumer can use them safely.

## Goals / Non-Goals

**Goals:**

- Recognize friend-link applications in newly created top-level comments on configured links,
  post, and single-page subjects.
- Use an explicitly selected AI Foundation language model for classification and field extraction.
- Create traceable, idempotent `PENDING` applications and reuse the existing administrator review
  flow.
- Keep AI Foundation optional and fail closed without affecting comment creation or non-AI plugin
  behavior.
- Preserve the existing administrator-initiated AI extraction flow while fixing the request and
  structured-output contracts required for shared reuse.
- Centralize application normalization, duplicate policy, and creation for both form and comment
  sources.

**Non-Goals:**

- Scanning comments created before the feature is enabled or while the plugin/AI dependency is
  unavailable.
- Processing replies, comment updates, approval transitions, or deletions.
- Automatically approving applications, creating formal links, replying to comments, or syncing a
  later comment edit into an application.
- Fetching an applicant's website to enrich missing model output.
- Adding a configurable prompt editor, persistent failed-attempt queue, manual replay action, or
  a broad privacy redesign of the existing "recent comments" Console tool.
- Fixing unrelated existing frontend type-check errors outside files touched by this change.

## Decisions

### 1. Add a separate automatic-recognition setting under the existing AI group

Keep the current `commentExtraction` settings and behavior for the administrator-initiated tool.
Add `commentApplicationRecognition` with:

- `enabled`, defaulting to `false`;
- a required AI Foundation language-model name constrained to available models that advertise
  structured output;
- a Repeater of subject rules, where each rule is exactly one of `LINKS`, `POST`, or
  `SINGLE_PAGE`;
- a conditionally required post or single-page metadata name for the corresponding rule type.

Recognition runs only when the global AI switch, the new switch, a model, at least one normalized
subject rule, and the optional AI integration are all available. Duplicate subject rules are
collapsed at runtime.

Using a separate model setting avoids coupling occasional administrator extraction to a continuous
background workload with different latency and cost requirements.

### 2. Consume only Comment add events through a single-worker Halo controller

Add a `Comment` controller/reconciler configured with:

- synchronization on startup disabled;
- update and delete matching disabled;
- one worker;
- filtering by settings and `subjectRef` inside reconciliation.

This uses Halo's managed controller lifecycle and queue while preserving strict new-event semantics.
It intentionally does not recover comments created before controller registration or during an
outage. The reconciler accepts all matching top-level `Comment` resources regardless of approved,
hidden, or owner state; `Reply` resources are not watched.

The controller worker is the synchronous boundary for the reactive AI call. The model request has a
30-second total timeout and at most two retries (three attempts total), so one comment cannot hold
the worker indefinitely.

### 3. Reuse a conditional internal AI analysis service

Extract model resolution, request execution, structured-output conversion, and common failure
mapping into an internal service protected by the existing AI Foundation availability condition.
Its public methods use plugin-owned request/result types so always-loaded plugin components do not
expose AI Foundation classes in fields, constructors, or signatures.

The service supports two request contracts:

- existing manual extraction, preserving its Console behavior;
- automatic recognition, returning a classification decision and optional application fields.

Both paths resolve `AiModelService` through `ExtensionGetter`; neither autowires across plugin
application contexts. The automatic path uses a fixed, versioned system prompt that labels comment
content as untrusted data. It uses a hand-built JSON Schema so only
`isLinkApplication` is structurally required and conditional business requirements are checked
after parsing.

The optional plugin dependency becomes
`ai-foundation?: ">=1.0.0-beta.4 & <2.0.0"`, while the Java API remains `compileOnly`.
AI Foundation absence must be covered by a classpath-isolation startup/registration test.

### 4. Send the minimum useful comment context to the model

The automatic prompt includes:

- raw comment text;
- configured subject type and current subject title;
- owner display name;
- the Email owner's `website` annotation when present.

It excludes email, IP address, user agent, and unrelated account identifiers. Email is copied
locally only after a positive decision. No tools or external fetching are enabled.

The recognition output is:

- required `isLinkApplication`;
- optional `url`, `displayName`, `logo`, `description`, `backlink`, and `feedUrls`.

When the decision is positive, URL resolution is model URL then owner website. Display-name
resolution is model value, owner display name, then normalized URL host. If no valid HTTP(S) URL is
available, no application is created.

### 5. Introduce a shared LinkApplication creation service

Move form application normalization, validation, duplicate lookup, and construction into a shared
service used by `/links/apply` and the comment controller. The service accepts plugin-owned origin
data and returns an explicit outcome such as created, duplicate, or invalid.

URL comparison uses a conservative canonical key: trim input, require HTTP(S), lowercase scheme
and host, remove a default port and fragment, and normalize an empty root path. It preserves the
original URL for display and does not collapse HTTP into HTTPS or discard non-root path/query
information.

The duplicate matrix is:

| Incoming source | Existing state/source | Result |
| --- | --- | --- |
| Any | Formal `Link` with canonical URL | Skip/reject |
| Any | `PENDING` or `APPROVED` application | Skip/reject |
| Comment | Rejected form application | Skip |
| Form | Rejected form application | Reject, preserving current policy |
| Form | Rejected comment application | Allow |
| Comment | Rejected comment application | Skip |

Comment-name lookup is a separate stable idempotency check so the same Comment can never create a
second application even if its URL presentation changes.

### 6. Add minimal origin data to LinkApplication

Add an optional `origin` object:

- `type`: `FORM` or `COMMENT`;
- `comment.name`: metadata name of the source Comment for `COMMENT` origins.

New form records write `FORM`; recognized records write `COMMENT` with the source Comment name.
Records without `origin` remain valid and are presented as historical applications. The Comment
name is a stable reference used to load the current subject and raw content when an administrator
opens application details. Derived Comment data and AI implementation details are not duplicated
in the LinkApplication.

Register indexes needed for comment-name idempotency and source-aware application queries. This is
an additive schema change and requires OpenAPI/client regeneration.

### 7. Extend the existing review UI instead of creating a second queue

The existing pending count and application list remain the single review queue. Add a source badge,
and load the current comment subject, source link, and raw content in the application details when
the source Comment remains available.

Extend the AI status contract so the UI can distinguish class presence from an enabled
`AiModelService`. If recognition is configured but cannot run, show a non-blocking warning on the
link management page. Model/provider/network failures after model resolution remain runtime
failures and do not make comment creation fail.

### 8. Repair only the shared AI baseline needed by this change

Before reusing the existing manual flow:

- fix the generated-client request-property mismatch;
- return `400` for an empty extraction request body;
- replace record-derived structured-output requirements with a schema that permits optional
  fields;
- test generic Map results, omitted optional values, malformed output, and the dependency-absent
  path;
- fix the stale `LinkRouterTest` constructor so backend tests compile.

Other pre-existing TypeScript nullability failures remain outside this change unless they occur in
a file directly modified here.

## Risks / Trade-offs

- [Risk] A newly created spam or hidden comment is sent to the selected model before moderation.
  → Mitigation: restrict processing to explicitly configured subjects, keep recognition disabled by
  default, send minimal identity data, and retain administrator review.
- [Risk] Comments created while recognition is unavailable are permanently missed.
  → Mitigation: make this explicit in settings/status messaging; this is the chosen new-event-only
  behavior and avoids surprising historical scans.
- [Risk] Model classification can create false-positive pending applications.
  → Mitigation: never create a formal Link automatically, retain a reference to the source
  Comment, and allow a later form submission after a rejected comment-origin false positive.
- [Risk] URL lookup and create are not transactionally unique across the extension store.
  → Mitigation: serialize automatic processing through one worker, use stable comment idempotency,
  centralize duplicate checks, and test concurrent/repeated delivery behavior.
- [Risk] Optional AI classes can leak into an always-loaded component during later refactoring.
  → Mitigation: keep AI types inside conditionally loaded implementation classes and enforce an
  absent-classpath registration test.
- [Risk] Editing or deleting the source Comment changes or removes the source context visible from
  an existing application.
  → Mitigation: keep the extracted application fields unchanged and clearly show when the source
  Comment is unavailable.
- [Risk] Full frontend type-check may remain red because of unrelated baseline failures.
  → Mitigation: add targeted tests, fix errors in touched files, record remaining baseline
  diagnostics, and do not report the full check as passing unless it actually does.

## Migration Plan

1. Deploy the additive `LinkApplication.origin` schema, indexes, settings, and optional AI adapter.
2. Existing applications remain valid with no migration; the UI treats missing origin as
   historical.
3. Existing AI settings and the manual extraction tool retain their keys and behavior.
4. Automatic recognition remains disabled until an administrator selects a model and adds at least
   one source.
5. Rollback disables the new controller and UI. Existing origin fields are additive and can remain
   stored, but operators should avoid editing those applications with an older plugin if preserving
   unknown fields is required.

## Open Questions

None. Product, privacy, dependency, failure, lifecycle, duplicate, and migration semantics were
settled during exploration.
