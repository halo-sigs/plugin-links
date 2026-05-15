## Why

`LinkFinderImpl.groupBy()` performs an N+1 query: it fetches all link groups first, then issues a separate query for each group to retrieve its links. With many groups this creates unnecessary database round-trips. The same information can be obtained with exactly two queries (groups + all links) and grouped in memory.

## What Changes

- Refactor `LinkFinderImpl.groupBy()` to:
  - Fetch all groups and all links in parallel via `Mono.zip`
  - Group links by `groupName` in memory using `Collectors.groupingBy`
  - Attach each group's links to its `LinkGroupVo` in a single pass
  - Append the implicit ungrouped group only if there are links without a `groupName`
- Keep `listBy(String)` unchanged — it remains the public per-group API

## Capabilities

### New Capabilities
- *(none)*

### Modified Capabilities
- *(none — implementation optimization with no spec-level behavior change)*

## Impact

- **Backend**: `LinkFinderImpl.java` only
- **Frontend**: None
- **API**: None — response shape and order are preserved
- **Dependencies**: None
