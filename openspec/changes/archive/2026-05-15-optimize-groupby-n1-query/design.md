## Context

`groupBy()` currently calls `listAllGroups()` (1 query) and then `concatMap`s over each group calling `listBy(groupName)` (N queries). Each `listBy` call hits the extension store with a field-selector filter on `spec.groupName`. For a site with 10 groups this means 11 queries; the cost grows linearly.

## Goals / Non-Goals

**Goals:**
- Reduce `groupBy()` from N+1 queries to 2 queries.
- Preserve the existing output order and ungrouped semantics.

**Non-Goals:**
- Changing `listBy(String)` behavior.
- Modifying `LinkPublicQueryService` interface.
- Adding caching.

## Decisions

### 1. Use `Mono.zip` to fetch groups and links in parallel
- **Rationale**: Both queries are independent. Running them concurrently minimizes latency.

### 2. Group links in memory with `Collectors.groupingBy`
- **Rationale**: Straightforward Java Stream API. Links without a `groupName` are bucketed under `"ungrouped"`.

### 3. Keep ungrouped link handling identical to current behavior
- **Rationale**: Ungrouped is an implicit group. It should only appear when there are actually ungrouped links, and it should come last.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Memory pressure with very large link counts | Friendship link plugins typically have modest data sizes; full-table fetch is acceptable |
| `ungrouped` key collision with a real group named "ungrouped" | Already handled by existing `UNGROUPED_NAME` constant; behavior unchanged |
