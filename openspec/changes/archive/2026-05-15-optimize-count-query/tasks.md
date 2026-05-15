## 1. Core Implementation

- [x] 1.1 Replace `client.listBy(Link.class, listOptions, PageRequestImpl.ofSize(1))` with `client.countBy(Link.class, listOptions)` in `LinkPublicQueryServiceImpl.count()`
- [x] 1.2 Replace `flatMap(links -> Mono.just((int)links.getTotal()))` with `map(Long::intValue)`
- [x] 1.3 Remove unused `PageRequestImpl` import if no longer needed elsewhere in the file

## 2. Verify

- [x] 2.1 Run `./gradlew build` to confirm compilation and tests pass
- [x] 2.2 Verify `count()` still returns the same value as before (semantic equivalence)
