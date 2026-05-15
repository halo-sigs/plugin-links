## 1. Fix groupComparator sorting direction

- [x] 1.1 In `LinkPublicQueryServiceImpl.groupComparator()`, change priority comparison from descending (`Integer.compare(p2, p1)`) to ascending (`Integer.compare(p1, p2)`)
- [x] 1.2 In the same comparator, change `creationTimestamp` comparison from descending (`t2.compareTo(t1)`) to ascending (`t1.compareTo(t2)`) to align with `defaultLinkSort()`

## 2. Verify

- [x] 2.1 Run `./gradlew build` to confirm the project compiles
- [x] 2.2 Manually verify that `groupBy()` returns groups ordered by ascending priority (lower priority first), matching the admin UI and `sortLinkGroups` endpoint behavior
