## Context

`LinkPublicQueryServiceImpl.listAllGroups()` sorts LinkGroups using an in-memory `groupComparator()` before mapping them to `LinkGroupVo`. The comparator currently orders groups by `priority` in **descending** order (`Integer.compare(p2, p1)`).

However, the rest of the system treats `priority` as an ascending index:
- `LinkFinderImpl.defaultLinkSort()` uses `asc("spec.priority")`
- `LinkRouter.defaultLinkSort()` uses `asc("spec.priority")`
- The frontend fetches both links and groups with `sort: ["spec.priority,asc"]`
- The `sortLinkGroups` console endpoint assigns priorities as `0, 1, 2...` in list order

This mismatch causes `groupBy()` (and any other consumer of `listAllGroups()`) to return groups in the reverse of the intended order.

## Goals / Non-Goals

**Goals:**
- Align `groupComparator()` with the system's ascending-priority semantics.
- Ensure `groupBy()` returns groups in the same order as the admin UI and `sortLinkGroups` endpoint.

**Non-Goals:**
- Changing Link sort behavior (already correct).
- Adding new APIs or changing existing API contracts.
- Frontend changes.

## Decisions

**Change `groupComparator()` from descending to ascending priority.**
- Rationale: The `sortLinkGroups` endpoint and frontend both treat lower priority values as "first in list." Making `groupComparator()` consistent removes the discrepancy without needing to change any other code.
- Alternative considered: Changing everything else to descending. Rejected because it would require touching the frontend, the sort endpoints, and `defaultLinkSort()`, with a much larger blast radius.

**Also change the secondary `creationTimestamp` sort from descending to ascending.**
- Rationale: `defaultLinkSort()` uses `asc("metadata.creationTimestamp")` as the secondary key. Keeping `groupComparator()` aligned means the tie-breaker order is the same for both Links and LinkGroups.

## Risks / Trade-offs

- **[Risk]** Themes relying on the current reversed group order will see a behavior change.
  - **Mitigation**: This is a bug fix — the current descending order contradicts the documented `priority` semantics. Theme authors should already be expecting ascending order to match the admin UI.
