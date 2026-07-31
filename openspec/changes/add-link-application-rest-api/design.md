## Context

Visitor applications currently enter through `GET /links/apply/captcha` and
`POST /links/apply/submit`. The POST accepts only form encoding, runs behind Halo CSRF, associates
the CAPTCHA through a `Path=/links` cookie, and can negotiate either a redirect or a plugin-owned
JSON envelope. The cookie, CSRF, and form body make that endpoint unsuitable for mini programs,
server integrations, or cross-origin page scripts even though its response can be JSON.

The reusable domain boundary already exists: `LinkApplicationService.create(Submission)` performs
validation, canonical duplicate detection, pending-capacity enforcement, persistence, and
notification. Process-local coordination serializes duplicate and capacity decisions. CAPTCHA
generation, challenge storage, rendering bounds, and per-IP admission are also centralized, but the
current public service methods couple challenge identification to the Form cookie.

Halo mounts plugin `CustomEndpoint` routes under `/apis/{groupVersion}`, exempts `/apis/**` from
CSRF by default, applies its deployment-level CORS policy there, and renders thrown
`ResponseStatusException` instances as `application/problem+json`. The renderer supplies the
standard Problem Details fields plus `requestId` and `timestamp`, but it does not copy exception
response headers. The existing public API group and OpenAPI grouping already cover
`api.link.halo.run/v1alpha1`.

## Goals / Non-Goals

**Goals:**

- Provide anonymous, cookie-free JSON endpoints for issuing a visitor CAPTCHA and creating a link
  application from general HTTP clients.
- Keep the native Form and REST transports on one settings, security, validation, duplicate,
  capacity, persistence, and notification workflow.
- Preserve `FORM` as the stored origin for both visitor transports.
- Use Halo's global Problem Details handling and stable problem type URIs for REST failures.
- Publish typed OpenAPI operations and complete Form, cURL, and browser integration guidance.
- Remove the beta-only negotiated JSON response from the native Form endpoint so each transport has
  one request and response contract.

**Non-Goals:**

- Querying, editing, or cancelling an anonymously created application.
- Adding API credentials, idempotency keys, a separate REST enable switch, or an `API` origin.
- Adding field restrictions that do not also exist for native Form submissions.
- Adding plugin-owned CORS settings, parsing forwarding headers, or introducing distributed
  CAPTCHA and rate-limit storage.
- Adding a mini-program-specific SDK, a new Console screen, new dependencies, or exception
  internationalization.

## Decisions

### 1. Add a public CustomEndpoint beside the native Form router

The REST transport uses the existing public group and exposes:

- `POST /apis/api.link.halo.run/v1alpha1/link-applications/captcha`
- `POST /apis/api.link.halo.run/v1alpha1/link-applications`

Both operations are anonymous and receive only the minimum `create` grants. Application creation
uses the `link-applications` resource grant. Halo parses a two-segment `POST` such as the agreed
CAPTCHA path as a non-resource request, so CAPTCHA issuance uses one exact non-resource URL grant
instead of a wildcard. No application-management permission is added. The existing OpenAPI
grouping already matches the path prefix, so annotated endpoint and DTO types flow into the
checked-in OpenAPI document and generated TypeScript client without another grouping rule.

The plugin does not install a second CORS filter or origin allowlist. `/apis/**` remains governed by
Halo's deployment-level CORS configuration. REST clients do not need cookies and browser examples
use `credentials: "omit"`. Halo's existing `/apis/**` CSRF exemption applies to these cookie-free
operations; the native Form endpoint keeps Halo CSRF.

**Alternatives considered:**

- Extending `/links/apply/submit` to accept JSON would retain Cookie/CSRF and response-negotiation
  coupling and would not create a reliable cross-origin contract.
- A new path outside `/apis/**` would require duplicate security, CORS, and OpenAPI integration.
- Plugin-owned CORS settings would conflict with deployment policy and add an unrelated
  configuration surface.

### 2. Separate CAPTCHA challenge mechanics from transport adapters

CAPTCHA issuance is refactored into a transport-neutral operation that returns the opaque challenge
identifier, PNG bytes, and lifetime. Both public adapters share the existing generation limiter,
bounded store, renderer gate, alphabet, expiry, and capacity.

The native Form adapter continues to return `image/png`, writes the identifier to its existing
HttpOnly cookie, and invalidates the previous cookie-associated challenge when replacing an image.
The REST adapter returns JSON with `challengeId`, a `data:image/png;base64,...` image, and
`expiresInSeconds=300`; it writes no cookie and always sets `Cache-Control: no-store`.

Verification similarly has one transport-neutral core that atomically consumes an explicit
identifier before comparing the normalized answer. The Form adapter extracts the identifier from
the cookie and expires the cookie. The REST adapter reads `challengeId` and `captchaCode` from the
JSON body. A REST client can issue multiple independent challenges, bounded by the shared
generation limiter and store; there is no client identity whose earlier token should be replaced.

Any verification attempt consumes the challenge. Malformed JSON cannot identify a challenge and
therefore fails before verification; every decoded submission attempt consumes its supplied
challenge even when later rate, validation, duplicate, capacity, or availability checks fail.

### 3. Map one JSON request DTO into the shared Submission

The REST request contains `url`, `displayName`, `logo`, `description`, `email`, `backlink`,
`feedUrls`, `challengeId`, and `captchaCode`. `url`, `displayName`, `challengeId`, and
`captchaCode` are required. `feedUrls` is an array rather than the native Form's newline-delimited
text.

Transport mapping trims strings, treats omitted, null, and blank optional strings as null, treats a
missing, null, or empty `feedUrls` as an empty list, trims list elements, and discards blank
elements. Unknown JSON fields are ignored for additive compatibility. The resulting
`LinkApplicationService.Submission` uses origin `FORM`.

The endpoint checks the shared visitor-submission setting, verifies and consumes the explicit
CAPTCHA, applies the existing shared per-IP submission limiter, and only then calls the shared
creation service. It does not duplicate validation, canonicalization, duplicate, capacity, or
notification rules.

### 4. Share admission state across both visitor transports

Form and REST CAPTCHA generation use the same per-IP generation limiter and outstanding challenge
capacity. Form and REST application POSTs use the same one-per-IP-per-minute submission limiter.
Switching transports cannot bypass either quota.

The current process-local model is retained deliberately. The plugin does not parse
`X-Forwarded-For` itself, and this change does not make CAPTCHA, limiter, duplicate coordination, or
capacity coordination distributed. Deployments remain responsible for routing and effective
remote-address behavior.

### 5. Return a minimal created result

Successful REST creation returns `201 Created` with:

```json
{
  "id": "link-app-...",
  "status": "PENDING"
}
```

`id` is the created LinkApplication metadata name already returned by the shared creation result.
The response contains no submitted fields or resource representation and does not advertise a
`Location`, because no anonymous GET operation exists. Notification publication failure after
successful persistence remains a successful `201`.

The API does not accept an idempotency key. If a response is lost after persistence, retrying the
same URL follows the existing duplicate rules and may return `409`.

### 6. Delegate REST failures to Halo Problem Details

The REST adapter converts expected outcomes to `ResponseStatusException` instances, or narrow
subclasses, with safe Chinese detail text and stable Problem `type` URIs. Halo's global handler
adds `title`, `status`, `instance`, `requestId`, and `timestamp`. The plugin does not maintain a
second JSON error envelope, `code`, or `field` contract.

The mapping is:

| Outcome | Status | Problem type |
| --- | ---: | --- |
| Invalid field input | `400` | `https://halo.run/probs/invalid-link-application` |
| Invalid CAPTCHA | `400` | `https://halo.run/probs/invalid-link-application-captcha` |
| Submission disabled | `403` | `https://halo.run/probs/link-application-disabled` |
| Duplicate | `409` | `https://halo.run/probs/duplicate-link-application` |
| Pending capacity reached | `409` | `https://halo.run/probs/link-application-capacity-reached` |
| Submission rate limit | `429` | `https://halo.run/probs/request-not-permitted` |
| Operational failure | `503` | `https://halo.run/probs/link-application-unavailable` |

Malformed JSON and unsupported media types retain Halo/Spring's native `400` and `415` Problem
Details. Field validation problems add Halo-style `errors: string[]`. Clients branch on
`status + type`, not localized or otherwise mutable `detail`.

The 429 exception adds `retryAfterSeconds` to the Problem Detail body. It does not promise a
`Retry-After` response header because Halo's global exception renderer does not copy exception
headers. Directly constructing only the 429 response would undermine the chosen single error path.

### 7. Narrow the native Form endpoint to one representation

`POST /links/apply/submit` continues to accept only
`application/x-www-form-urlencoded`. It no longer inspects `Accept`, returns a plugin JSON
envelope, emits `406`, or varies on `Accept`. Every plugin-owned business outcome keeps the existing
`303` redirect contract. An unsupported request media type returns `415` without processing an
application.

This intentionally removes the negotiated JSON contract published in `v2.3.0-beta.1`. Theme
JavaScript that needs structured results migrates to the REST endpoint instead of retaining a
compatibility switch or alias.

### 8. Specify and test the public boundary rather than implementation branches

OpenAPI describes request, CAPTCHA, created-result, and Problem Details shapes and both public
operations. Public documentation separates the native Form and REST contracts, includes cURL and
browser `fetch` examples, explains Halo-owned CORS, challenge refresh, machine-readable problem
types, and the absence of cookies, status queries, and idempotency.

Targeted tests cover each transport independently and assert their shared settings, admission, and
creation behavior. Form regression tests prove all negotiation branches are gone. REST tests cover
anonymous RBAC, JSON decoding and normalization, CAPTCHA single use, shared rate limits, domain
outcomes, Halo Problem Details, generated OpenAPI, and sensitive-data boundaries.

## Risks / Trade-offs

- [The explicit challenge identifier is exposed to page code] → Keep the answer secret, use secure
  random identifiers, retain five-minute single-use consumption, and never log either value.
- [Two transports drift in validation or admission order] → Keep transport code limited to decoding,
  CAPTCHA association, and response mapping; route both into the same limiter and creation service.
- [A lost `201` is ambiguous on retry] → Document that no idempotency contract exists and that a
  later duplicate conflict may mean the first request succeeded.
- [A multi-instance request reaches a different process than its challenge] → Retain the existing
  process-local limitation explicitly and leave distributed state to a separate change.
- [Deployment CORS disables a browser integration] → Document that CORS is Halo-owned and verify
  non-browser clients remain unaffected.
- [Removing the beta Form JSON contract breaks an early adopter] → Publish the REST replacement and
  migration example before the stable release; do not carry two asynchronous contracts forward.
- [Problem Detail headers differ from direct responses] → Put retry timing in
  `retryAfterSeconds` and specify that `Retry-After` is not part of the REST contract.

## Migration Plan

1. Add the REST endpoint, explicit CAPTCHA transport, anonymous grants, and OpenAPI contract while
   preserving the native Form redirect behavior.
2. Remove Form response negotiation, its JSON response builders, negotiation tests, and async Form
   documentation in the same pre-stable change.
3. Regenerate the TypeScript client and publish REST cURL/browser examples with a beta migration
   note.
4. Validate both transports and release the replacement before `2.3.0` stable.

Rollback removes the new endpoints and anonymous grants and restores the beta negotiation code if
required. No Extension schema or persisted data migration is involved.

## Open Questions

None.
