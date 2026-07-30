## 1. Lock the Path Contract with Tests

- [x] 1.1 Update the `LinkRouterTest` submission matrix to call
  `POST /links/apply/submit` while preserving every existing HTML, JSON, CSRF, validation,
  rate-limit, capacity, and failure assertion.
- [x] 1.2 Update CAPTCHA route tests and request fixtures to call
  `GET /links/apply/captcha` while preserving image, cache, cookie, security-limit, and disabled
  behavior assertions.
- [x] 1.3 Add focused router regressions proving `POST /links/apply` and `GET /links/captcha` are no
  longer handled by plugin-links.

## 2. Move the Public Routes

- [x] 2.1 Change the `LinkRouter` predicates to expose only `POST /links/apply/submit` and
  `GET /links/apply/captcha`, reusing the existing handlers without changing processing behavior.
- [x] 2.2 Verify HTML result redirects still target `/links?...`, the CAPTCHA Cookie remains scoped
  to `/links`, and no compatibility alias or redirect is registered for either old path.

## 3. Update Active Integration Guidance

- [x] 3.1 Replace the old form action, CAPTCHA image, refresh, and JavaScript URLs throughout
  `dev/theme-api.md`, preserving both ordinary-form and negotiated-JSON guidance.
- [x] 3.2 Update the visitor-submission help text in `settings.yaml` to name
  `/links/apply/submit`.
- [x] 3.3 Search active source, tests, resources, documentation, and main specs for stale
  `/links/apply` or `/links/captcha` endpoint references, excluding historical archive artifacts,
  and review every remaining match.

## 4. Validate the Migration

- [x] 4.1 Run focused route, CAPTCHA, CSRF, and security tests against the new paths.
- [x] 4.2 Run the full backend test suite and `./gradlew build`.
- [x] 4.3 Run `openspec validate change-link-application-action-paths --strict`,
  `git diff --check`, and a local Halo smoke test confirming the new routes work and the old routes
  are unavailable.
