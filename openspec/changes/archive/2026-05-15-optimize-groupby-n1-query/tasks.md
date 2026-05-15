## 1. Refactor groupBy to eliminate N+1 queries

- [x] 1.1 Replace `concatMap(group -> listBy(...))` pattern in `LinkFinderImpl.groupBy()` with parallel fetch of all groups and all links via `Mono.zip`
- [x] 1.2 Add in-memory grouping of links by `groupName` using Java Streams
- [x] 1.3 Map each group's links into its `LinkGroupVo` in a single pass
- [x] 1.4 Preserve ungrouped semantics: only emit ungrouped group if there are links without `groupName`, append it last

## 2. Verify

- [x] 2.1 Run `./gradlew build` to confirm compilation and tests pass
- [x] 2.2 Verify output order matches previous implementation (groups in priority/creation order, ungrouped last)
