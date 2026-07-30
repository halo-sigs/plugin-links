## Why

The visitor application endpoints currently split submission and CAPTCHA actions across
`/links/apply` and `/links/captcha`. Grouping both actions under `/links/apply/` gives the public
theme contract a consistent action namespace and makes each endpoint's purpose explicit.

## What Changes

- **BREAKING** Move form submission from `POST /links/apply` to
  `POST /links/apply/submit`.
- **BREAKING** Move CAPTCHA image generation from `GET /links/captcha` to
  `GET /links/apply/captcha`.
- Remove the old action paths instead of retaining aliases or redirects.
- Update theme integration documentation, settings help text, tests, and examples to use the new
  paths.
- Preserve all existing request formats, content negotiation, response bodies, status codes,
  redirects, CSRF behavior, CAPTCHA semantics, rate limiting, and application processing order.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `link-application`: Change the public visitor submission and CAPTCHA endpoint paths without
  changing their behavior.

## Impact

- Backend: `LinkRouter` public route predicates.
- Theme-facing API: HTML form actions, CAPTCHA image and refresh URLs, and JavaScript examples.
- Configuration UI: visitor-submission help text in `settings.yaml`.
- Tests: route, CSRF, CAPTCHA, negotiation, and request fixtures that reference the old paths.
- Existing themes using either old path must update; no frontend API client regeneration or new
  dependency is required.
