## 1. Extract LinkQuery to standalone class

- [x] 1.1 Create `src/main/java/run/halo/links/LinkQuery.java` with the `LinkQuery` class extracted from `LinkRouter`
- [x] 1.2 Update `LinkQueryEndpoint.java` to reference `new LinkQuery(...)` instead of `new LinkRouter.LinkQuery(...)`

## 2. Create LinkEndpoint for console APIs

- [x] 2.1 Create `src/main/java/run/halo/links/LinkEndpoint.java` implementing `CustomEndpoint` with groupVersion `console.api.link.halo.run/v1alpha1`
- [x] 2.2 Move `GET links` handler and `listLinkByGroup` logic from `LinkRouter` into `LinkEndpoint`
- [x] 2.3 Move `GET link-detail` handler and `getLinkDetail` logic from `LinkRouter` into `LinkEndpoint`
- [x] 2.4 Move `POST links/-/sort` handler and `sortLinks` logic from `LinkRouter` into `LinkEndpoint`
- [x] 2.5 Move `POST link-groups/-/sort` handler and `sortLinkGroups` logic from `LinkRouter` into `LinkEndpoint`
- [x] 2.6 Inject `ReactiveExtensionClient` into `LinkEndpoint`

## 3. Clean up LinkRouter

- [x] 3.1 Remove the `@Bean linkRoute()` method and `nested()` method from `LinkRouter`
- [x] 3.2 Remove `getLinkDetail`, `sortLinks`, `sortLinkGroups`, `listLinkByGroup`, `listLink` methods from `LinkRouter`
- [x] 3.3 Remove the `LinkQuery` nested class from `LinkRouter`
- [x] 3.4 Clean up unused imports from `LinkRouter`
- [x] 3.5 Verify `LinkRouter` only contains theme routing (`linkTemplateRoute`) and related helpers

## 4. Update RBAC and build config

- [x] 4.1 Update `roleTemplate.yaml`: replace `api.plugin.halo.run` rules with `console.api.link.halo.run` rules, following `plugin-photos` pattern
- [x] 4.2 Add `nonResourceURLs` for sort endpoints if needed
- [x] 4.3 Update `build.gradle` OpenAPI `pathsToMatch`: remove `/apis/api.plugin.halo.run/...`, add `/apis/api.link.halo.run/v1alpha1/**`

## 5. Regenerate API client and update frontend

- [x] 5.1 Run `./gradlew generateApiClient` to regenerate the TypeScript client
- [x] 5.2 Update `console/src/api/index.ts` to replace `ApiPluginHaloRunV1alpha1LinkApi` with `ConsoleApiLinkHaloRunV1alpha1LinkApi`
- [x] 5.3 Update `use-link-fetch.ts` to use the new generated request types
- [x] 5.4 Update `LinkForm.vue` to use the new client for `getLinkDetail`
- [x] 5.5 Update `LinksSortableCard.vue` to use the new client for `sortLinks`
- [x] 5.6 Update `GroupSortModal.vue` to use the new client for `sortLinkGroups`
- [x] 5.7 Run `./gradlew build` to verify backend compiles
- [x] 5.8 Run `cd console && pnpm type-check` to verify frontend types

## 6. Verify

- [x] 6.1 Start `./gradlew haloServer` and verify console UI loads at `/console/links`
- [x] 6.2 Verify link list, create, edit, delete, sort operations work
- [x] 6.3 Verify group sort and group delete operations work
- [x] 6.4 Verify theme `/links` page still renders correctly
