## Context

`POST /links/apply` currently accepts only `application/x-www-form-urlencoded` and turns every
plugin-owned result into a `303 See Other` response whose `Location` contains `applied`, `field`,
`value`, and `message` query parameters. This is a good progressive-enhancement baseline for theme
forms, but a same-origin `fetch` follows the redirect and receives the rendered `/links` HTML page.
An asynchronous theme must infer the result from the final URL and cannot distinguish results from
HTTP status alone.

The existing handler also owns important sequencing that must not change: effective settings are
checked first, Halo CSRF runs before the route, CAPTCHA is consumed before the formal submission
limit, duplicate detection precedes pending-capacity reporting, and notification failure does not
turn a persisted application into a failed submission.

## Goals / Non-Goals

**Goals:**

- Add a stable JSON representation for same-origin theme JavaScript without changing the existing
  HTML form request or redirect contract.
- Give every plugin-owned JSON business result a stable code, display message, optional field, and
  accurate HTTP status.
- Preserve all existing security checks, side effects, result ordering, and CAPTCHA consumption.
- Make content negotiation, unsupported media types, rate-limit timing, caching, and client fallback
  behavior explicit and testable.
- Provide a complete asynchronous integration example without prescribing how a theme exposes the
  existing CSRF token or availability model variable to its JavaScript.

**Non-Goals:**

- Accepting JSON or multipart request bodies.
- Supporting cross-origin, headless, mobile, or third-party API clients.
- Adding CORS, an application-status endpoint, an idempotency key, or a JavaScript SDK.
- Changing Halo CSRF responses or any other platform-owned error representation.
- Exposing a created LinkApplication name or object to anonymous visitors.
- Adding settings, Extension fields, Console behavior, generated API clients, internationalization,
  migrations, or dependencies.

## Decisions

### 1. Negotiate the response on the existing endpoint

The request body remains `application/x-www-form-urlencoded`. The handler selects JSON only when
the request explicitly accepts `application/json` with a higher preference than HTML. A missing
`Accept`, `*/*`, an HTML preference, or an equal JSON/HTML preference selects the legacy redirect
representation. When neither JSON nor HTML is acceptable, the endpoint returns `406 Not
Acceptable` without a plugin JSON envelope.

Both negotiated representations add `Vary: Accept`; JSON responses also add `Cache-Control:
no-store`.

**Alternatives considered:**

- A separate JSON endpoint would duplicate a public entry point and make it easier for validation,
  CAPTCHA, rate limiting, and creation ordering to drift.
- Treating any occurrence of `application/json` as decisive would ignore quality weights and could
  surprise clients that prefer HTML.
- Making `fetch` parse the followed redirect URL would retain the extra page render and would not
  provide meaningful result status codes.

### 2. Reject unsupported request media explicitly without adding request formats

The exact POST path must identify unsupported request media before form parsing. A client that
negotiated JSON receives `415 Unsupported Media Type` with the normal JSON envelope and
`UNSUPPORTED_MEDIA_TYPE`; other clients receive the status without a plugin JSON guarantee.

This remains response negotiation, not a JSON application API. Supporting a JSON request body is
excluded because the form contains only text fields and a second decoder would add no user
capability.

### 3. Adapt one internal outcome to HTML or JSON

Settings, CAPTCHA, rate limiting, form parsing, validation, duplicate checks, capacity evaluation,
persistence, and notification handling execute once. Representation-specific response builders map
the resulting outcome either to the exact existing redirect or to JSON. They must not reimplement
business rules or reorder side effects.

The JSON envelope is:

```json
{
  "status": "error",
  "code": "VALIDATION_FAILED",
  "field": "url",
  "message": "URL格式错误"
}
```

`status` is `success` or `error`. `field` is omitted when no form field applies. The response never
contains a submitted `value`, a generic `data` member, or LinkApplication resource data.

Published codes retain their meanings; later versions may add codes. Themes branch on `code`, use
`message` only as display text, and fall back to a global message for unknown codes or fields they
cannot associate with a control. Current Chinese messages remain user-facing text and are not
frozen as program identifiers.

**Alternative considered:** RFC Problem Details for errors would split success and failure into
different body models and add machinery that a small theme form does not need.

### 4. Map domain outcomes to accurate HTTP status

The JSON representation uses:

| Outcome | Status | Code | Field |
| --- | ---: | --- | --- |
| Created | `201` | `APPLICATION_CREATED` | none |
| Submission disabled | `403` | `APPLICATION_DISABLED` | none |
| Invalid CAPTCHA | `422` | `INVALID_CAPTCHA` | `captchaCode` |
| Field validation | `422` | `VALIDATION_FAILED` | failing field |
| Duplicate | `409` | `DUPLICATE_APPLICATION` | `url` |
| Submission rate limit | `429` | `RATE_LIMITED` | none |
| Pending capacity reached | `409` | `CAPACITY_REACHED` | none |
| Operational creation failure | `503` | `APPLICATION_UNAVAILABLE` | none |
| Unsupported request media | `415` | `UNSUPPORTED_MEDIA_TYPE` | none |

Capacity is a conflict with current queue state, not a client-specific rate limit. An effective
disabled result, including the existing fail-closed settings behavior, remains forbidden rather
than being reclassified as operational unavailability. Notification publication failure remains a
successful creation because persistence already completed.

### 5. Expose accurate retry timing only for JSON rate limits

`LinkApplicationRateLimiter` must return an admission result containing whether the request is
allowed and, when rejected, the remaining whole seconds until retry. The calculation remains atomic
with the existing per-IP timestamp update and rounds a positive fractional remainder up so the
header never permits an early retry.

Only the JSON `429` response includes `Retry-After`. Adding it to a legacy `303` could be interpreted
as delaying the redirected GET rather than describing when to resubmit the form.

### 6. Keep platform and transport failures outside the envelope

The stable envelope covers plugin-owned business outcomes and the JSON-negotiated `415`. Halo
Security rejects invalid CSRF before the handler and keeps its native `403`. A `406`, platform
failure, network error, or non-JSON response is not parsed as this plugin's envelope.

Theme guidance must inspect the response `Content-Type` before decoding JSON and display a local
generic failure when no stable envelope is available. This boundary avoids plugin-specific changes
to Halo's global security chain.

### 7. Preserve CAPTCHA semantics and document client refresh behavior

JSON negotiation does not change one-time CAPTCHA state or processing order. Invalid CAPTCHA, rate
limit, duplicate, field validation, capacity, and operational-failure results consume or invalidate
the challenge exactly as their existing redirect equivalents do. Asynchronous themes reload the
CAPTCHA after these business failures and close or reset the form after success.

The reference integration also uses `URLSearchParams`, explicitly requests JSON, disables repeated
submission while pending, renders `message` with text-safe DOM APIs, focuses a matching `field`,
handles unknown codes and non-JSON failures, and refreshes the CAPTCHA. It does not prescribe a
`meta`, `data-*`, hidden-input, or inline-script strategy for exposing `csrfToken` and
`linkApplicationEnabled`; theme authors choose how to consume the already documented template
variables.

## Risks / Trade-offs

- [Negotiation edge cases select the wrong representation] → Specify quality, wildcard, absence,
  and tie behavior explicitly and cover each case with route tests.
- [A catch-all POST route for `415` shadows the valid form route] → Keep matching limited to the
  exact `/links/apply` path and test valid, invalid, and missing content types.
- [Themes treat display messages or the initial code set as closed] → Document codes as stable but
  additive and require unknown-code fallback.
- [Retry timing is rounded down and causes an immediate second rejection] → Calculate the remaining
  duration atomically and round up to a positive whole second.
- [JSON and redirect results drift] → Map a shared internal outcome through two response adapters
  and maintain one table-driven regression matrix for both representations.
- [Asynchronous validation failures reuse a consumed CAPTCHA] → Document and test refresh behavior
  for every post-verification business failure.

## Migration Plan

1. Deploy the additive negotiated response while keeping the HTML path as the default.
2. Publish the complete asynchronous theme example and stable code table.
3. Theme authors may opt in by sending `Accept: application/json`; existing themes require no
   changes.
4. Rollback can remove JSON negotiation without data migration, although themes that opted into the
   new contract would need to stop requesting JSON.

## Open Questions

None.
