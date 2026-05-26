## Why

Friend links can silently become unreachable, and reciprocal links can disappear without the
site owner noticing. The Console needs an explicit way to check link reachability and backlinks
without running external network checks automatically at plugin startup.

## What Changes

- Add link verification settings to `Link.spec` so each link can optionally define a fixed
  backlink scan URL.
- Add link verification status to `Link.status` for reachability and backlink results.
- Add a Console API for explicit verification runs over all links, one group, or a selected
  set of links.
- Trigger a single verification run after Console link creation or editing succeeds.
- Display reachability and backlink status on `LinkBadge`.
- Keep verification trigger-based only: no reconciler-driven checks, no startup scans, and no
  scheduled recurring verification.

## Capabilities

### New Capabilities

- `link-verification`: Link reachability and backlink verification behavior, status model,
  Console triggers, and Badge display.

### Modified Capabilities

- `console-link-api`: Add a Console endpoint for triggering link verification runs.
- `ssrf-protection`: Apply existing URL-fetch safety requirements to link verification
  reachability and backlink-scan requests.

## Impact

- Backend: `Link` extension schema/status, a verification service, Console endpoint DTOs/routes,
  bounded asynchronous execution, and focused unit tests.
- Frontend: generated API client refresh, `LinkForm`, create/edit modals, `LinksCard`,
  `LinkList`, and `LinkBadge` status display.
- Operations: verification may issue external HTTP(S) requests only after explicit user actions
  or successful Console save/edit flows.
