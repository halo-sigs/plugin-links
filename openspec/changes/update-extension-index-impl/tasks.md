## 1. Migrate Index Declarations

- [x] 1.1 Update `LinkPlugin.start()` to use `IndexSpecs.single()` instead of `IndexAttributeFactory.simpleAttribute()` and `new IndexSpec()`
- [x] 1.2 Change `spec.priority` index type from `String` to `Integer` for both `Link` and `LinkGroup`
- [x] 1.3 Verify import changes (`IndexSpecs` replaces `IndexAttributeFactory` and `IndexSpec` constructor usage)

## 2. Migrate Query Construction

- [x] 2.1 Update `LinkRouter.LinkQuery.toListOptions()` to use `Queries` instead of `QueryFactory`
- [x] 2.2 Update `LinkFinderImpl.listBy()` and `groupBy()` to use `Queries` instead of `QueryFactory`
- [x] 2.3 Verify all `QueryFactory` imports are removed and replaced with `Queries`

## 3. Build Verification

- [x] 3.1 Run `./gradlew build` to confirm the plugin compiles cleanly after migration
- [x] 3.2 Run `./gradlew test` to ensure no regressions (tests may be absent, but verify build succeeds)
