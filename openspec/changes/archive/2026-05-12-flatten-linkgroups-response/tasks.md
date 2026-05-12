## 1. Backend — Flatten linkgroups response

- [x] 1.1 Add `listAllGroups(ListOptions options)` to `LinkPublicQueryService` interface returning `Mono<List<LinkGroupVo>>`
- [x] 1.2 Implement `listAllGroups` in `LinkPublicQueryServiceImpl` (reuse existing `client.listAll` + `groupComparator` + `toGroupVo` logic, skip pagination)
- [x] 1.3 Update `LinkGroupQueryEndpoint` GET `/linkgroups` to call `listAllGroups` and return `List<LinkGroupVo>` instead of `ListResult<LinkGroupVo>`
- [x] 1.4 Build backend (`./gradlew build`) to verify compilation

## 2. Frontend — Regenerate client and update call sites

- [x] 2.1 Regenerate OpenAPI TypeScript client (`./gradlew generateApiClient`)
- [x] 2.2 Update `use-group-fetch.ts` to call the regenerated API directly instead of using `paginate()`, consuming `LinkGroupVo[]`
- [x] 2.3 Update `GroupSortModal.vue` to call the regenerated API directly instead of using `paginate()`, consuming `LinkGroupVo[]`
- [x] 2.4 Run frontend build (`pnpm build`) to verify compilation (type-check script has pre-existing config issue)
