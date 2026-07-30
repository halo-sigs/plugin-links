## Context

The public visitor workflow currently exposes `POST /links/apply` for form submission and
`GET /links/captcha` for CAPTCHA images. Both are theme-facing contracts implemented by
`LinkRouter`; the submission handler also supports negotiated HTML redirects and JSON results.

The requested paths place both actions below `/links/apply/`. This is a breaking URL change for
themes, but it does not require a data migration, a new request format, or a second business flow.
Archived OpenSpec changes remain historical and are not rewritten.

## Goals / Non-Goals

**Goals:**

- Expose submission only at `POST /links/apply/submit`.
- Expose CAPTCHA generation only at `GET /links/apply/captcha`.
- Update every active code, test, setting-help, documentation, and main-spec reference.
- Preserve the existing submission and CAPTCHA contracts apart from their paths.

**Non-Goals:**

- Retaining compatibility aliases, redirects, or a deprecation window for the old paths.
- Changing the `/links` theme page or the `/links?...` result redirect targets.
- Changing CSRF, CAPTCHA cookies, request media types, JSON negotiation, rate limits, validation,
  persistence, notifications, or response payloads.
- Rewriting archived change artifacts or generating a Console API client.

## Decisions

### 1. Replace both routes without compatibility aliases

`LinkRouter` will match only `POST /links/apply/submit` and
`GET /links/apply/captcha`. The old `POST /links/apply` and `GET /links/captcha` paths will no
longer be registered.

Keeping aliases would reduce immediate theme breakage, but it would leave two public contracts to
test and maintain indefinitely. Redirecting is also unsuitable for the POST endpoint because it
can change request methods or require replaying a consumed form. The requested change is therefore
treated as an explicit breaking migration.

### 2. Reuse the existing handlers and business flow unchanged

Only the WebFlux route predicates change. The existing submission and CAPTCHA handler functions,
response negotiation, status mapping, ordering, and side effects remain shared and unchanged.

Creating new handler methods or forwarding between routes would add a second execution path without
providing new behavior.

### 3. Keep landing redirects and CAPTCHA cookie scope unchanged

HTML submission results continue redirecting to `/links` with the existing query parameters.
The CAPTCHA cookie keeps its `/links` path, which already covers the new endpoints and the theme
page. Narrowing it to `/links/apply` would be an unrelated security-contract change and would not
improve this migration.

### 4. Treat active documentation and specifications as the migration surface

The theme API guide, settings help text, route tests, CSRF tests, CAPTCHA fixtures, and current
`link-application` specification will use the new paths. Tests will also establish that the old
paths are no longer exposed.

Files under `openspec/changes/archive/` remain unchanged because they record the contracts that
were approved at the time of those archived changes.

## Risks / Trade-offs

- [Existing themes continue posting to the old paths] → Mark the change as breaking and update all
  bundled integration examples and settings guidance in the same release.
- [A partial replacement leaves stale active references] → Search active source, tests, docs,
  resources, and main specs for both old paths and fail the task if any remain outside archives.
- [The path move accidentally changes response behavior] → Reuse the same handlers and run the
  existing negotiation, CSRF, CAPTCHA, redirect, and JSON outcome test matrix against the new paths.
- [Old routes remain reachable unintentionally] → Add focused router tests asserting both obsolete
  paths are not routed.

## Migration Plan

1. Update backend route predicates and all active callers, examples, fixtures, and specifications.
2. Run focused route tests, the full test suite, build, strict OpenSpec validation, and stale-path
   searches that exclude archived changes.
3. Release the plugin and require themes to update their form action and CAPTCHA URLs together.
4. If rollback is required, restore the old route predicates and matching active documentation;
   no stored application data needs conversion.

## Open Questions

None.
