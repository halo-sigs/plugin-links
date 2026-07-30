## 1. Backend Subject Display

- [x] 1.1 Extend the origin-Comment response with an optional plugin-owned subject display DTO
  containing `title`, `url`, and `kindName` while retaining the existing Comment fields.
- [x] 1.2 Resolve the recorded Comment's subject reactively through
  `ExtensionGetter<CommentSubject>` and preserve a successful Comment response when no display is
  available.
- [x] 1.3 Add endpoint tests for a resolved subject display, an unavailable display that still
  returns Comment context, a missing original Comment, and application-scoped Comment selection.

## 2. OpenAPI Client

- [x] 2.1 Regenerate the OpenAPI document and TypeScript client after the response DTO changes.
- [x] 2.2 Verify the generated origin-Comment model exposes nullable subject display fields without
  changing the route or permission contract.

## 3. Console Source Navigation

- [x] 3.1 Render a resolved subject as `kindName · title` using a new-tab external link, with
  `kindName` and generic-text fallbacks for blank display values.
- [x] 3.2 Render `来源页面不可用` when the subject display is absent and remove subject editor-route
  derivation from raw refs.
- [x] 3.3 Keep the general Comment-management link while removing the visible Comment resource name
  and every other resource `metadata.name` fallback from link-application views.
- [x] 3.4 Update frontend unit tests to cover resolved display labels, blank-value fallbacks,
  unavailable subjects, external navigation metadata, and the no-visible-resource-name rule.

## 4. Validation

- [x] 4.1 Run backend tests covering the origin-Comment endpoint and subject resolution.
- [ ] 4.2 Run frontend formatting, lint, type checking, unit tests, and production build.
- [x] 4.3 Run the full Gradle build and strict OpenSpec validation for
  `improve-link-application-source-navigation`.
