## Why

The existing visitor submission endpoint can return JSON but still requires a same-origin,
form-encoded request, CSRF token, and cookie-bound CAPTCHA. That contract is unsuitable for
general HTTP clients such as mini programs, server integrations, and cross-origin page scripts, so
the plugin needs a dedicated public REST API without creating a second application workflow.

## What Changes

- Add anonymous public REST endpoints under `api.link.halo.run/v1alpha1` for issuing a
  cookie-free CAPTCHA challenge and creating a link application from a JSON request.
- Reuse the existing visitor-submission switch, CAPTCHA implementation, rate limits, validation,
  duplicate rules, capacity, persistence, notification, and `FORM` origin semantics across the
  native Form and REST transports.
- Return `201 Created` with the created application identifier and status, and delegate REST errors
  to Halo's Problem Details exception handling with stable problem type URIs.
- Add the minimum anonymous RBAC grants, OpenAPI schemas, generated client operations, and public
  integration documentation for the REST contract.
- **BREAKING** Remove `Accept`-based JSON response negotiation from
  `POST /links/apply/submit`; the native Form endpoint accepts only form-encoded submissions and
  returns only its redirect contract. Asynchronous integrations must use the REST API.

## Capabilities

### New Capabilities

- `link-application-rest-api`: Defines the anonymous JSON submission, explicit CAPTCHA challenge,
  CORS, Problem Details, success response, RBAC, and OpenAPI contracts for general HTTP clients.

### Modified Capabilities

- `link-application`: Extends visitor submission invariants across both transports and narrows the
  native Form endpoint to redirect-only behavior.

## Impact

- Backend routing and endpoint code, visitor CAPTCHA challenge transport, REST request/response
  DTOs, exception mapping, shared submission admission, and anonymous role templates.
- Existing Form route tests and documentation because negotiated JSON responses are removed.
- Public OpenAPI output and the generated TypeScript API client; no new Console workflow or
  frontend screen is introduced.
- Public integrations that adopted the negotiated JSON response in `v2.3.0-beta.1` must migrate to
  the new REST endpoint before the stable release.
