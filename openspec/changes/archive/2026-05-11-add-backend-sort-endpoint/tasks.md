## 1. Backend Sort Endpoints

- [x] 1.1 Create `SortRequest.java` DTO with a `List<String> names` field
- [x] 1.2 Add `POST /links/-/sort` handler in `LinkRouter.java` that receives `SortRequest`, fetches each `Link` by name via `ReactiveExtensionClient`, updates `spec.priority` to its index, and saves sequentially
- [x] 1.3 Add `POST /link-groups/-/sort` handler in `LinkRouter.java` with the same logic for `LinkGroup`
- [x] 1.4 Verify both endpoints return HTTP 200 OK and handle empty lists gracefully

## 2. OpenAPI Client Regeneration

- [x] 2.1 Add new route paths to `haloPlugin.openApi.groupingRules.pathsToMatch` in `build.gradle` if needed
- [x] 2.2 Run `./gradlew generateApiClient` to regenerate the TypeScript client
- [x] 2.3 Verify generated client includes the new sort endpoints

## 3. Frontend Integration

- [x] 3.1 Call `linksConsoleApiClient.link.sortLinks` directly in `LinksSortableCard.vue` instead of batch-update loop
- [x] 3.2 Call `linksConsoleApiClient.link.sortLinkGroups` directly in `GroupSortModal.vue` instead of batch-update loop
- [x] 3.4 Remove obsolete batch-update logic from the frontend

## 4. Verification

- [x] 4.1 Run `./gradlew build` to ensure backend compiles
- [x] 4.2 Run `cd console && pnpm type-check` to ensure frontend types are valid
- [x] 4.3 Run `cd console && pnpm lint` to ensure frontend passes linting
