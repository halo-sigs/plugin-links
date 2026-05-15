## Why

`LinkFinderImpl.groupBy()` returns link groups in an order that does not match the priority semantics used by the rest of the system. The `sortLinkGroups` endpoint assigns priorities as `0, 1, 2...` (lower first), and the frontend lists groups with `spec.priority,asc`. However, `LinkPublicQueryServiceImpl.groupComparator()` sorts groups in **descending** priority order, causing the group order from `groupBy()` to be reversed relative to user expectations and the admin UI.

## What Changes

- Fix `groupComparator()` in `LinkPublicQueryServiceImpl` to sort LinkGroups by priority in **ascending** order (lowest priority first), consistent with `defaultLinkSort()` for Links and the frontend's `spec.priority,asc` sort parameter.
- Update the secondary sort key (`creationTimestamp`) in `groupComparator()` from descending to ascending to align with `defaultLinkSort()`.

## Capabilities

### New Capabilities

_(none — this is a behavior fix, not a new feature)_

### Modified Capabilities

_(none — no spec-level API contract changes, only internal sorting correction)_

## Impact

- **Backend**: `LinkPublicQueryServiceImpl.groupComparator()` — sorting logic change only.
- **Affected APIs**: `LinkFinder.groupBy()` (Finder API for themes), `LinkGroupQueryEndpoint` (public REST API), and `LinkRouter.simpleGroups` (Thymeleaf template variable).
- **Frontend**: No changes required; frontend already uses `spec.priority,asc`.
- **Breaking?** No — this corrects a bug where group order did not match the intended priority semantics.
