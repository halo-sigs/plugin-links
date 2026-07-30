## Why

The public link-application endpoint is designed around browser navigation and encodes every
business result in a `303` redirect URL. Same-origin theme JavaScript can submit the form
asynchronously, but it must follow the redirect, download the rendered links page, and parse its
final URL instead of receiving a stable response that it can display directly.

## What Changes

- Add response content negotiation to `POST /links/apply`: clients that explicitly prefer
  `application/json` receive a stable JSON result and accurate HTTP status, while existing HTML
  form clients retain the exact current `303` redirect contract.
- Keep `application/x-www-form-urlencoded` as the only supported request body and return an
  explicit unsupported-media-type result for JSON-negotiated requests with another content type.
- Define a lightweight JSON envelope with stable result codes, display messages, and an optional
  form field, without reflecting submitted values or exposing the created LinkApplication.
- Return an accurate `Retry-After` header for JSON rate-limit responses and add representation
  headers required by the negotiated contract.
- Document a complete same-origin asynchronous form flow, including result handling, unknown-code
  fallback, non-JSON errors, submission state, and CAPTCHA refresh behavior.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `link-application`: Add the negotiated JSON response contract for visitor submissions while
  preserving all existing HTML redirects, security checks, creation ordering, and CAPTCHA
  consumption semantics.

## Impact

- Backend: public link-application routing, JSON response DTOs, content negotiation, and submission
  rate-limit admission details.
- Theme contract: additive same-origin asynchronous submission support on the existing
  `/links/apply` endpoint; ordinary forms remain compatible.
- Documentation and tests: update the theme API and add exhaustive negotiation, JSON result,
  response-header, rate-limit, CAPTCHA, and redirect-regression coverage.
- No Console/frontend bundle change, generated API client change, JSON request body, CORS support,
  public status API, JavaScript SDK, setting, Extension field, CSRF customization, migration, or new
  dependency.
