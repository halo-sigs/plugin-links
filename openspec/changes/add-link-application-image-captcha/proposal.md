## Why

The anonymous `/links/apply` endpoint currently relies only on a process-local per-IP submission
limit, so simple automation can still create persistent application spam. The plugin needs a
first-party challenge that raises the cost of casual scripted submissions without requiring site
owners to register or configure an external CAPTCHA service.

## What Changes

- Add an in-plugin image CAPTCHA that is required whenever visitor link applications are enabled.
- Add a public, no-store PNG endpoint that issues short-lived, single-use challenges through a
  secure cookie.
- Bound challenge generation by IP, global drawing concurrency, expiry, and store capacity while
  preserving the existing application submission rate limit.
- Validate and consume the CAPTCHA before the shared LinkApplication creation service, so Comment,
  Console, and internal creation paths remain unchanged and CAPTCHA data is never persisted.
- Preserve the existing `303` redirect contract with one generic CAPTCHA error that does not echo
  the submitted answer or application fields.
- Document the plain-HTML theme integration and add security, concurrency, CSRF, cookie, and routing
  tests.
- Establish the initial visitor-application theme contract with a required CAPTCHA image and
  `captchaCode` field. The unreleased feature has no legacy form, compatibility fallback, or
  migration path.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `link-application`: Require and document a bounded first-party image CAPTCHA for the public
  visitor application form while leaving all other application sources unchanged.

## Impact

- **Backend:** `LinkRouter`, new CAPTCHA generation/state/limiting components, and packaged
  open-license font resources.
- **Theme API:** `GET /links/captcha` and the required `captchaCode` form field; existing
  `linkApplicationEnabled`, CSRF, and redirect contracts remain in use.
- **Tests:** Challenge lifecycle, generation protection, routing, cookie security, application
  ordering, and real CSRF WebFilter coverage.
- **Unaffected:** Console UI/API, Comment recognition, `LinkApplicationService`, persisted
  LinkApplication schema, generated TypeScript clients, and external CAPTCHA providers.
