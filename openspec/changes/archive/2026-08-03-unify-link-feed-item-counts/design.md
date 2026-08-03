## Context

The Console RSS updates header has three independent collection entry points: favorite, read-later,
and hidden items. `LinkFeedList` currently queries only the hidden count, while favorite and read-later
are exposed through cursor-paginated lists with no total. The SQLite feed-item table already stores all
three boolean states, so this change needs a read-only aggregation contract rather than a schema change.

The existing hidden-count endpoint is an internal Console API introduced for the hidden-item workflow.
The frontend and backend are shipped together, so it can be replaced by a single summary endpoint while
the generated client, RBAC template, documentation, and tests are regenerated or updated as one change.

## Goals / Non-Goals

**Goals:**

- Return exact global counts for hidden, visible favorite, and visible read-later cached items in one
  Console request.
- Display all three counts in the existing RSS updates header, including resolved zero values.
- Keep saved-item counts aligned with the lists users open: hidden rows are excluded from favorite and
  read-later counts, while hidden count includes every hidden row.
- Keep the count query fresh after every mutation that can change a counted collection.
- Replace the old hidden-count API without leaving duplicate count contracts.

**Non-Goals:**

- Changing the existing unread-summary API or its per-link unread data.
- Adding counts to public REST or Finder responses.
- Adding a total field to cursor-paginated item pages.
- Changing feed-item retention, stable IDs, or SQLite schema.
- Counting unique articles across overlapping favorite and read-later collections; the two saved counts
  remain independent.

## Decisions

### One unified Console summary contract

Add `GET /apis/console.api.link.halo.run/v1alpha1/rss/items/-/summary` with operation ID
`getLinkFeedItemSummary` and a `LinkFeedItemSummary` response containing required numeric fields:
`hiddenCount`, `favoriteCount`, and `readLaterCount`.

The endpoint returns all three fields, including zero values. `hiddenCount` counts rows where
`hidden = 1`. `favoriteCount` counts rows where `hidden = 0 AND favorite = 1`, and `readLaterCount`
counts rows where `hidden = 0 AND read_later = 1`. A row may contribute to both saved counts.

This replaces the existing hidden-count endpoint rather than extending its misleadingly narrow response
contract. The unread-summary endpoint remains separate because it also returns per-link unread data and
serves different sidebar behavior.

### One storage aggregation

Replace the store-level hidden-only count contract with one summary operation that computes all three
values in one SQLite query. Conditional aggregate expressions keep the values from one database read and
avoid three independent count requests. Existing non-null boolean columns and defaults are sufficient;
no migration is required.

### One Vue Query summary root

Replace the hidden-count composable and cache key with one summary composable and cache key. The header
reads the three fields from that result. It renders `(0)` during the initial summary request and any
background refetch, including when cached data exists, then replaces those placeholders with the resolved
values. Request failures rely on Halo's global API error interceptor and leave the labels usable without a
number.

### Centralized invalidation of counted collections

Invalidate the summary query after successful favorite and read-later mutations, hidden-state changes,
and item-opening flows that remove read-later state. Also invalidate it in feed refresh/initial-refresh
and link-deletion success paths so a cached summary cannot survive a collection-changing operation.
Existing normal-list, hidden-list, and unread-summary invalidations remain in place.

### View-only authorization and generated contract updates

Authorize the new GET operation through `plugin:links:view` using the same `rss/-` resource pattern and
`items/summary` resource name that Halo derives for the route. Remove the old hidden-count RBAC rule.
Regenerate the OpenAPI TypeScript client from the backend contract; do not hand-edit generated files.
Update the tracked OpenAPI document and developer REST reference with the new endpoint and remove the
old endpoint.

## Risks / Trade-offs

- **[Risk]** Removing `hidden-count` breaks external callers of the Console API. → **Mitigation:** the
  plugin ships the backend and Console together, the repository has no other caller, and the breaking
  removal is documented in the proposal and current API reference.
- **[Risk]** Saved and hidden counts have intentionally different visibility predicates. → **Mitigation:**
  encode the predicates in store tests, endpoint schema descriptions, and frontend behavior tests.
- **[Risk]** A mutation path may forget to invalidate the new summary root. → **Mitigation:** centralize
  the cache key and cover favorite, read-later, hidden/unhidden, refresh, and deletion paths in tests.
- **[Risk]** Generated client or API documentation can drift from the route removal. → **Mitigation:**
  regenerate from the backend OpenAPI document and verify no old operation/path references remain outside
  historical archive artifacts.

## Migration Plan

1. Add the summary DTO, store aggregation, endpoint, view RBAC rule, and backend tests.
2. Regenerate OpenAPI artifacts and the TypeScript client, then replace the frontend hidden-count query
   with the unified summary query and update invalidation paths.
3. Update the current REST documentation and frontend/backend tests; leave archived OpenSpec artifacts
   unchanged as historical records.
4. Verify the packaged plugin with targeted backend and frontend checks, API reference search, and
   `git diff --check`.
5. Rollback is a plugin-version rollback: the unchanged SQLite schema is compatible, and the previous
   plugin version restores its old endpoint and frontend together.

## Open Questions

None. The route, response fields, visibility predicates, authorization, invalidation behavior, and
breaking-removal scope were agreed before implementation.
