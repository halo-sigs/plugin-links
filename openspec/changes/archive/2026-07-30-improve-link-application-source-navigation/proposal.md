## Why

Comment-origin link applications currently derive their source labels from raw subject references
and route administrators to Console editors instead of the public page where the comment was
submitted. This exposes internal resource names in the UI and makes source inspection less useful
during review.

## What Changes

- Enrich the application-scoped origin-Comment response with an optional resolved subject display
  containing its public title, URL, and kind.
- Resolve subject displays through Halo's `CommentSubject` extension point so posts, single pages,
  the Links page, and third-party comment subjects share one backend path.
- Open the resolved public source in a new browser tab from application review and detail views,
  without exposing Console editor routes.
- Preserve original Comment content and timestamps when the subject display cannot be resolved,
  while keeping a missing original Comment as a not-found response.
- Stop rendering Comment or subject resource `metadata.name` values in link-application Console
  views.
- Keep the existing general Comment-management entry without adding a Halo Core comment deep link.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `link-application`: Refine Comment-origin source inspection to resolve public subject displays,
  define graceful subject-unavailable behavior, and prohibit visible resource metadata names.

## Impact

- Backend: the origin-Comment DTO and endpoint subject-resolution flow.
- Frontend: application source-context rendering and the generated OpenAPI client.
- Specifications and tests: link-application source-context requirements, backend endpoint coverage,
  and frontend presentation utilities/components.
- Dependencies: reuse the existing Halo `CommentSubject` and `ExtensionGetter` APIs; no new runtime
  dependency or Halo version increase is required.
