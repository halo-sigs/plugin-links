## 1. Create LinkImportModal.vue component

- [x] 1.1 Create `console/src/components/LinkImportModal.vue`
- [x] 1.2 Implement paste textarea with placeholder showing expected format
- [x] 1.3 Add group selector dropdown (fetch existing groups via `useLinkGroupsQuery`)
- [x] 1.4 Add "在线解析" checkbox with warning hint
- [x] 1.5 Add parse button and basic line-split + `|` delimiting logic

## 2. Implement online scraping with preview

- [x] 2.1 Add parsed result preview table with inline editing for each field
- [x] 2.2 Integrate `linksConsoleApiClient.link.getLinkDetail` for online scraping
- [x] 2.3 Implement concurrency limit (max 3 parallel requests)
- [x] 2.4 Show per-row status: success (scraped), error (failed/timeout), manual (no scrape)
- [x] 2.5 Allow users to edit or uncheck rows before import

## 3. Implement batch import

- [x] 3.1 Query current max priority in target group (same pattern as `LinkCreationModal.vue`)
- [x] 3.2 Build `createLink` payloads for checked rows with auto-incrementing priorities
- [x] 3.3 Implement chunked creation (chunk size 5, parallel via `Promise.all`, same pattern as `LinksCard.vue`)
- [x] 3.4 Invalidate `QK_GROUPS_WITH_LINKS` query key after successful import
- [x] 3.5 Show success toast with count of imported links

## 4. Add import entry point

- [x] 4.1 Add import button to `LinkList.vue` (next to the existing "新建" / "添加链接" button)
- [x] 4.2 Or add import option to the existing dropdown menu in `LinksCard.vue`

## 5. Verify

- [x] 5.1 Run `cd console && pnpm type-check` to verify TypeScript types
- [x] 5.2 Run `./gradlew build` to verify full build
- [x] 5.3 Start `./gradlew haloServer` and test the import flow end-to-end
