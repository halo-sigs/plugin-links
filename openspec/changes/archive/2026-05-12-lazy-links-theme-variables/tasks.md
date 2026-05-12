## 1. Refactor LinkRouter for lazy variables and group filtering

- [x] 1.1 Add `LinkPublicQueryService` dependency injection to `LinkRouter`
- [x] 1.2 Add `BLOCKING_TIMEOUT` constant and `_templateId` key constant
- [x] 1.3 Refactor route handler to read `group` query parameter from `ServerRequest`
- [x] 1.4 Implement `loadLinks(String group)` helper that returns all links or group-filtered links
- [x] 1.5 Create `LazyContextVariable<List<LinkVo>>` for `links` variable
- [x] 1.6 Create `LazyContextVariable<List<LinkGroupVo>>` for `simpleGroups` variable
- [x] 1.7 Create `LazyContextVariable<List<LinkGroupVo>>` for `groups` variable (preserving existing `groupBy()` behavior)
- [x] 1.8 Build model map with `links`, `simpleGroups`, `groups`, `group`, `pluginName`, `linksTitle`, and `_templateId`

## 2. Verify and clean up

- [x] 2.1 Compile the project (`./gradlew compileJava`) to verify no type errors
- [x] 2.2 Review imports and remove any unused ones
- [x] 2.3 Verify the handler uses `HashMap` instead of `Map.of` (needed for mutable map and more than 10 entries)
