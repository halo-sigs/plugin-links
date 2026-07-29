## Context

Visitor applications use a same-origin, URL-encoded `POST /links/apply` route owned by
`LinkRouter`. The route currently checks the application settings, applies a process-local
one-request-per-IP-per-minute limit, parses the form, and calls the shared
`LinkApplicationService`. The shared service is also used by Comment recognition, so CAPTCHA
handling must remain at the public form boundary.

The desired protection is deliberately narrow: raise the cost of casual scripted submissions
without requiring an account, external provider, site key, or secret. It is not intended to defeat
professional OCR, CAPTCHA-solving services, distributed attacks, or a compromised client.

`plugin-comment-widget` demonstrates that Java2D image generation, short-lived local state, and an
HttpOnly challenge cookie work in a Halo plugin. Its implementation is not reusable as-is because
it uses predictable drawing, a small globally evictable cache, an unbounded generation endpoint,
and non-consuming verification. This change reimplements the pattern inside plugin-links with
bounded generation and atomic one-time consumption.

## Goals / Non-Goals

**Goals:**

- Require a first-party image CAPTCHA for every request through the public visitor application
  route whenever visitor applications are enabled.
- Keep plain HTML themes functional without requiring JavaScript.
- Make every challenge short-lived, single-use, bounded in memory, and safe under concurrent
  verification.
- Bound per-IP generation, global drawing concurrency, and total outstanding challenge state.
- Preserve Halo CSRF protection, the existing submission rate limit, and the `303` result contract.
- Keep CAPTCHA state out of LinkApplication resources and all non-form creation channels.

**Non-Goals:**

- External CAPTCHA providers, provider abstractions, site keys, or administrator CAPTCHA settings.
- Protection for Comment recognition, Console APIs, or internal LinkApplication creation.
- Strong bot detection, behavioral analysis, audio CAPTCHA, or a non-visual challenge.
- Multi-instance challenge sharing or persistence across plugin restarts.
- Retaining submitted application fields after a CAPTCHA failure.
- Field-length limits, duplicate-query optimization, AI quotas, or LinkApplication RBAC changes.

## Decisions

### 1. Couple CAPTCHA availability directly to visitor applications

`selfSubmissionEnabled()` remains the only effective visitor-channel switch. When it is true,
`GET /links/captcha` is available and `POST /links/apply` requires a valid CAPTCHA. When it is
false, the image endpoint returns `404` and the application route preserves its existing disabled
redirect.

There is no CAPTCHA setting, fallback, or legacy branch. Visitor applications have not been
released, so the CAPTCHA-protected form is the initial published theme contract rather than a
compatibility change.

**Alternatives considered:**

- An opt-in CAPTCHA switch leaves newly enabled public forms weak by default.
- Detecting whether a theme supplied CAPTCHA fields creates a trivial bypass.

### 2. Serve a PNG from the theme router and bind it with an HttpOnly cookie

Add `GET /links/captcha` to `LinkRouter`. A successful response has `Content-Type: image/png` and
`Cache-Control: no-store, no-cache, must-revalidate`. It writes a plugin-specific cookie with:

- path `/links`;
- max age equal to the five-minute challenge TTL;
- `HttpOnly`;
- `SameSite=Lax`;
- `Secure` when the effective request scheme is HTTPS.

The cookie contains only an opaque, cryptographically random challenge identifier. It is not bound
to IP, user, authentication, or User-Agent. After a new image is generated successfully, issuance
atomically invalidates the challenge named by any existing cookie and overwrites that cookie.
Therefore only one challenge is active per browser cookie jar. Multiple tabs may invalidate each
other's image; this is accepted for the first version.

The theme renders the image directly and submits one new `captchaCode` form field. JavaScript may
refresh the image by requesting it again, but page load, error redirect, and submission work without
JavaScript.

**Alternatives considered:**

- Returning JSON with a challenge identifier supports independent tabs but makes JavaScript
  mandatory.
- A hidden challenge identifier generated while rendering `/links` would create state for every
  page view and complicate refresh.
- A stateless signed token cannot provide strict one-time semantics without a replay store.
- A CustomEndpoint would require anonymous RBAC and OpenAPI surface with no benefit over the theme
  route.

### 3. Generate one fixed, locally rendered challenge format

The image is a fixed `160 x 48` PNG containing five case-insensitive alphanumeric characters.
Ambiguous characters such as `0`, `O`, `1`, `I`, and `l` are excluded. `SecureRandom` selects the
answer and all visual variation.

Characters use individual offsets and small rotations over a light background with limited lines
and noise points. A clearly licensed font is packaged with the plugin so rendering is deterministic
in headless containers and does not copy the reference plugin's Arial asset. Generation and PNG
encoding run off the event loop.

There are no administrator controls for type, length, font, or noise. This keeps validation and
resource bounds fixed and reflects the casual-automation threat model.

### 4. Store only bounded answer state and consume it atomically

A plugin-local challenge store holds at most 10,000 entries. Each entry contains only the normalized
answer and expiry instant; generated image bytes are never cached. Challenges expire five minutes
after issuance.

Issuance and capacity checks are concurrency-safe. Generation and verification lazily remove
expired entries; no cleanup scheduler is introduced. After cleanup, a full store rejects new
issuance with `503 Service Unavailable` instead of evicting an unexpired challenge. Plugin shutdown
clears the store.

Verification resolves the opaque identifier from the cookie and atomically removes the entry before
checking expiry or the answer. Submitted text is trimmed, rejected unless it is exactly five ASCII
characters, and compared without case sensitivity. Correct, incorrect, expired, missing, and
replayed attempts all consume or lack the challenge. Concurrent requests can therefore produce at
most one successful verification. Every verification attempt also expires the browser cookie.

**Alternatives considered:**

- Consuming only after success allows brute-force retries and concurrent replay.
- Persisting challenges in the extension store adds durable data and cleanup work for an ephemeral
  single-instance proof.
- Evicting the oldest live challenge lets a generator flood invalidate legitimate forms.

### 5. Protect image generation independently from application submission

A dedicated process-local limiter admits at most ten CAPTCHA images per remote IP per minute and
tracks at most 10,000 IP entries with bounded cleanup. It uses the server request remote address,
matching the existing submission limiter, and does not trust forwarding headers itself. A rejected
generation returns `429 Too Many Requests` with `Retry-After`.

When a new IP arrives at the tracking limit, the limiter removes expired entries and then its oldest
remaining tracking entry if necessary, matching the bounded behavior of the existing application
limiter.

At most four image renderings run concurrently. If all slots are occupied, generation fails
immediately with `503 Service Unavailable`; it does not build an unbounded worker queue. Capacity,
font, drawing, encoding, and challenge-store failures also return `503`. The endpoint always fails
closed.

The CAPTCHA generation limiter is separate from the existing application limiter so loading or
refreshing an image never consumes the one-per-minute submission allowance.

### 6. Verify before consuming the existing submission allowance

The effective request sequence is:

1. Halo's existing CSRF WebFilter rejects invalid cross-site requests.
2. `LinkRouter` loads settings and preserves the disabled redirect.
3. The route parses form data.
4. CAPTCHA verification atomically consumes the challenge.
5. The existing per-IP application limiter admits at most one validated submission per minute.
6. `LinkApplicationService` validates, deduplicates, and creates the application.

An incorrect CAPTCHA therefore does not consume the formal submission allowance. The separate
generation limit and one-attempt challenge semantics bound guessing.

No CAPTCHA object or field enters `LinkApplicationService`, `LinkApplication`, Comment recognition,
Console APIs, or generated clients.

### 7. Preserve a generic redirect failure contract

Missing, malformed, incorrect, expired, and replayed CAPTCHA submissions all redirect to:

`/links?applied=error&field=captchaCode&message=验证码错误或已过期，请重新输入`

The redirect never includes a `value` parameter for the answer and does not include the other
submitted application fields. Themes may retain form state with client-side code, but the plugin
does not persist a failed draft. Drawing and generation failures remain HTTP errors on the image
request rather than weakening POST verification.

### 8. Minimize observable and logged sensitive data

The implementation does not log CAPTCHA answers, opaque identifiers, cookies, application form
fields, or raw client IPs. Ordinary verification failures produce no server log. System failures
such as capacity exhaustion or drawing errors may log aggregate context without attacker-controlled
form data.

## Risks / Trade-offs

- [Risk] The fixed image can be solved by OCR or a human-solving service.
  → Mitigation: state the casual-automation threat model and retain IP limits and administrator
  review; use an external anti-bot system in a separate change if the threat changes.
- [Risk] Image-only verification is inaccessible to some visitors.
  → Mitigation: provide clear text, keyboard-operable refresh controls in theme guidance, and keep
  the entire visitor-application channel disableable; audio verification is out of scope.
- [Risk] Multiple tabs overwrite one cookie and make an older image fail.
  → Mitigation: return the same generic retry message and load a fresh image after redirect.
- [Risk] Restarting the plugin invalidates outstanding challenges.
  → Mitigation: challenges are explicitly ephemeral and errors instruct the visitor to retry.
- [Risk] Reverse-proxy topology can cause many visitors to share one remote address.
  → Mitigation: follow Halo's effective remote-address handling and document that plugin code does
  not parse untrusted forwarding headers.
- [Risk] Mandatory fail-closed behavior can temporarily disable applications during an internal
  rendering failure.
  → Mitigation: return explicit `503` responses, log aggregate system failures, and keep the master
  or visitor-submission setting available as an operational off switch.

## Migration Plan

None. Visitor applications have not been published, so implementation and theme documentation land
as one initial contract. No compatibility shim, legacy branch, data migration, setting migration,
alias, fallback, or staged compatibility rollout is required.

## Open Questions

None. The threat model, initial theme contract, challenge format, state model, limits, failure
behavior, accessibility boundary, tests, and change scope were confirmed during exploration.
