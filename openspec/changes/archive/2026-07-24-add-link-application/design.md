## Context

The plugin-links project manages friend links for Halo 2.0 CMS. Currently, all links must be created manually by administrators through the Console UI. There is no mechanism for site visitors to request links autonomously.

The project uses Halo's Extension system (`AbstractExtension`, `ReactiveExtensionClient`) for data persistence, Spring WebFlux for reactive backend, and Vue 3 + TypeScript for the Console frontend. Theme-facing pages use Thymeleaf templates with a Finder API (`linkFinder`).

Existing security infrastructure includes `LinkSecurityUtils` (SSRF prevention) and `SafeUrlFetcher` (URL validation). Existing automation includes link verification and RSS feed refresh, triggered from the frontend after link creation.

## Goals / Non-Goals

**Goals:**
- Allow anonymous visitors to submit link applications from theme templates via standard HTML forms
- Provide administrators with a lightweight approval workflow integrated into the existing LinkList page
- Support form value回填 on validation errors so theme developers can show error states without JavaScript
- Prevent duplicate submissions and basic abuse through rate limiting
- Reuse existing automation (verification, RSS refresh) upon approval

**Non-Goals:**
- Email notifications to applicants (no email sending infrastructure in the plugin)
- CAPTCHA or advanced anti-spam measures
- Automatic backlink verification at submission time (verification is manual during approval)
- Allowing applicants to select their own group (groups are assigned by administrators during approval)
- Public API for querying application status (applicants only see success/error via redirect)

## Decisions

### Decision: Use a new `LinkApplication` Extension instead of annotating `Link`
**Rationale:** A dedicated extension model keeps pending applications completely separate from approved links. This avoids the risk of `linkFinder` or other queries accidentally exposing unapproved links, and eliminates the need to add status-filtering logic to every existing Link query. The trade-off is a new extension model and additional Console UI, but the data isolation is worth it for correctness.

**Alternative considered:** Reuse `Link` with a `pending` annotation. Rejected because every `listBy`/`groupBy`/`random`/`count` query would need to filter out pending records, creating a maintenance burden and potential security holes.

### Decision: Theme endpoint uses `application/x-www-form-urlencoded` with 302 redirects
**Rationale:** Theme developers write plain HTML forms (`<form method="post" action="/links/apply">`) without JavaScript. After submission, a 302 redirect with query parameters is the standard way to communicate result state back to the theme template. Thymeleaf can read `${param.applied}`, `${param.field}`, `${param.value}`, and `${param.message}` to show success/error messages and repopulate fields.

**Alternative considered:** JSON API with AJAX submission. Rejected because it forces theme developers to write JavaScript, contradicting the goal of simple HTML forms.

### Decision: In-memory rate limiting (Caffeine or ConcurrentHashMap) instead of distributed rate limiter
**Rationale:** The plugin runs as a single-instance Halo plugin. A simple per-IP in-memory rate limiter is sufficient to prevent casual abuse. No Redis or external store is needed. The limit is lenient (1 request per minute) to avoid false positives while blocking scripts.

**Alternative considered:** No rate limiting. Rejected because an anonymous writable endpoint without any protection is easily exploited.

### Decision: Approve operation is a custom endpoint, not a standard extension PATCH
**Rationale:** Approval is a business operation that spans two extensions: it creates a `Link` and updates a `LinkApplication`. A single custom endpoint (`POST /linkapplications/{name}/approve`) encapsulates this transaction atomically and allows the frontend to trigger post-approval automation (verification + RSS refresh) in one call flow.

**Alternative considered:** Two separate API calls (PATCH application status + POST new Link). Rejected because it leaves a window where the application is approved but no Link exists, and requires the frontend to orchestrate multiple calls.

### Decision: Backlink verification is manual during approval, not automatic at submission
**Rationale:** Applicants often submit before adding the backlink to their site. Automatic verification at submission would almost always fail, providing no useful signal. Manual verification during approval lets administrators check when the backlink is expected to be in place.

### Decision: Rejected applications block resubmission by URL
**Rationale:** This prevents applicants from immediately resubmitting the same URL after rejection. The URL is the natural unique identifier for a link. If an administrator rejects a site, they typically don't want to see it again.

**Alternative considered:** Allow resubmission after rejection. Rejected because it creates a loop of repeated rejections for the same site.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| In-memory rate limiter resets on server restart, allowing a burst of submissions after restart | Acceptable for this threat model. The limiter is for casual abuse, not determined attacks. |
| Theme developers must implement form error handling manually in Thymeleaf | Document the query parameter contract clearly. Provide an example form snippet. |
| `LinkApplication` records accumulate over time (approved + rejected) | Administrators can manually delete. No automatic cleanup is implemented. |
| Approval modal becomes complex (edit fields + group select + verify button + approve/reject) | Use a drawer or two-level modal to keep the UI manageable. |
| Post-approval automation (verification + RSS) is triggered from frontend, not backend | If the admin closes the browser mid-flow, automation may not complete. Mitigation: the backend `approve` endpoint itself could trigger these, but current plugin architecture has them as frontend-initiated operations. Accept this limitation for consistency with existing link creation flow. |

## Migration Plan

No migration needed. This is a purely additive feature. Existing links, groups, and theme templates continue to work unchanged. Theme developers can opt-in by adding a form to their `links.html`.

## Open Questions

- Should the `LinkApplication` extension register index specs for `spec.url`, `spec.status` to optimize queries? (Likely yes, following the pattern of `Link` and `LinkGroup` indexes.)
- Should the approval detail view show the applicant's `feedUrls` in a way that allows administrators to preview or validate RSS feeds before approval?
