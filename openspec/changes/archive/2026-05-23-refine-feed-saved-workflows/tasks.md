## 1. Main Feed Simplification

- [x] 1.1 Remove favorite-state and read-later-state dropdowns from the primary RSS updates filter bar.
- [x] 1.2 Update the main feed composable state so the primary list only depends on link, group, and read-state filters.
- [x] 1.3 Keep existing per-item favorite, read-later, and read/unread actions working in the main feed.

## 2. Favorite Workflow

- [x] 2.1 Add a favorite entry to the RSS updates page header.
- [x] 2.2 Implement a favorites modal that loads feed items with `favorite=true`.
- [x] 2.3 Support opening articles, marking read/unread, and removing favorite state from the favorites modal.
- [x] 2.4 Add cursor pagination or load-more behavior for the favorites modal if the result set exceeds the page size.

## 3. Read-Later Workflow

- [x] 3.1 Add a read-later section above the main RSS updates list.
- [x] 3.2 Load read-later items independently with `readLater=true`.
- [x] 3.3 Support opening articles from the read-later section and remove them from read-later after the read state update succeeds.
- [x] 3.4 Support removing read-later state directly from the read-later section.
- [x] 3.5 Keep the read-later section compact and responsive across desktop and narrow Console viewports.

## 4. State Synchronization

- [x] 4.1 Ensure saved-state mutations update or refresh affected visible lists without a full page reload.
- [x] 4.2 Ensure removing favorite or read-later state removes the item from the corresponding saved list.
- [x] 4.3 Confirm the existing backend list filters and toggle endpoints are sufficient without OpenAPI regeneration.

## 5. Verification

- [x] 5.1 Run `pnpm --dir console type-check`.
- [x] 5.2 Run `pnpm --dir console lint`.
- [x] 5.3 Run `./gradlew build`.
- [x] 5.4 Validate the OpenSpec change with `openspec validate refine-feed-saved-workflows --strict`.
- [x] 5.5 Run a browser E2E smoke test for favorite modal, read-later section, and article-open state transitions.
