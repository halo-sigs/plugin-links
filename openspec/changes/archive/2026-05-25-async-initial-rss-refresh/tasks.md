## 1. Save Flow

- [x] 1.1 Inspect the current create/edit modal success handlers and identify the shared initial RSS refresh conditions.
- [x] 1.2 Update link creation so the modal closes and saved-link queries invalidate immediately after create succeeds.
- [x] 1.3 Update link editing so the modal closes and saved-link queries invalidate immediately after patch succeeds.
- [x] 1.4 Start the initial RSS refresh in the background when RSS is newly enabled or feed URLs changed, without awaiting it in the save mutation.

## 2. RSS Refresh Settlement

- [x] 2.1 Ensure the background refresh uses the generated `refreshLinkFeed` API and relies on Halo's global request error handling.
- [x] 2.2 Invalidate RSS subscription/status and feed item queries after the background refresh settles so the UI can reflect fetched items or failures.
- [x] 2.3 Keep manual RSS feed page refresh actions unchanged so they still await remote refresh before reloading the visible list.

## 3. Verification

- [x] 3.1 Add or update focused frontend tests if existing modal/composable test coverage can exercise the non-blocking save behavior.
- [x] 3.2 Run `pnpm --dir console type-check`.
- [x] 3.3 Run `pnpm --dir console test:unit`.
- [x] 3.4 Run `openspec validate async-initial-rss-refresh --strict`.
- [x] 3.5 Optionally smoke-test in a local Halo instance that create/edit modals close before slow RSS refresh completion.
