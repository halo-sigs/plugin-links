## 1. Backend — LinkApplication Extension Model

- [x] 1.1 Create `LinkApplication.java` extension model with `LinkApplicationSpec` (url, displayName, logo, description, email, backlink, feedUrls, status) and `LinkApplicationStatus`
- [x] 1.2 Register `LinkApplication` index specs in `LinkPlugin.java` for `spec.url`, `spec.status`, `spec.displayName`
- [x] 1.3 Verify plugin compiles and `LinkApplication` extension is registered on startup

## 2. Backend — Theme Submission Endpoint

- [x] 2.1 Create in-memory IP-based rate limiter utility (`LinkApplicationRateLimiter.java`) with 1 req/min per IP
- [x] 2.2 Add `POST /links/apply` route to `LinkRouter.java` accepting `application/x-www-form-urlencoded`
- [x] 2.3 Implement form validation (url format, required fields: url, displayName) with 302 redirect to `/links?applied=error&field=&value=&message=`
- [x] 2.4 Implement duplicate detection: reject if URL exists in PENDING or REJECTED status
- [x] 2.5 Implement success flow: create `LinkApplication(PENDING)`, redirect to `/links?applied=success`
- [x] 2.6 Add anonymous access rule for `POST /links/apply` in `roleTemplate.yaml`

## 3. Backend — Console Management APIs

- [x] 3.1 Create `LinkApplicationEndpoint.java` with Console REST endpoints:
  - `GET /apis/console.api.link.halo.run/v1alpha1/linkapplications` — list with status filter
  - `GET .../linkapplications/{name}` — get by name
  - `DELETE .../linkapplications/{name}` — delete
- [x] 3.2 Add `POST .../linkapplications/{name}/approve` endpoint:
  - Accept modified fields + groupName in request body
  - Create `Link` with provided values
  - Update `LinkApplication` status to `APPROVED`
  - Return created `Link` name
- [x] 3.3 Add `POST .../linkapplications/{name}/reject` endpoint — update status to `REJECTED`
- [x] 3.4 Add `POST .../linkapplications/{name}/verify` endpoint — trigger backlink verification using existing `SafeUrlFetcher` + `LinkSecurityUtils`
- [x] 3.5 Add RBAC role templates for Console endpoints (require `link-manage` role)

## 4. Frontend — Console UI Components

- [x] 4.1 Create `LinkApplicationListModal.vue` — modal showing pending applications (url, displayName, creationTime)
- [x] 4.2 Create `LinkApplicationDetailDrawer.vue` — drawer with:
  - Editable form fields (url, displayName, logo, description)
  - Group selection dropdown (populate from existing LinkGroups)
  - "Verify Backlink" button with result display
  - "Approve" and "Reject" action buttons
- [x] 4.3 Create `use-link-application.ts` composable with Vue Query hooks:
  - `useLinkApplications(status)` — list query
  - `useApproveLinkApplication()` — approve mutation
  - `useRejectLinkApplication()` — reject mutation
  - `useDeleteLinkApplication()` — delete mutation
  - `useVerifyBacklink()` — verify mutation
- [x] 4.4 Modify `LinkList.vue`:
  - Add pending count alert card at top (shows count of PENDING applications)
  - Click opens `LinkApplicationListModal`

## 5. Frontend — Post-Approval Automation

- [x] 5.1 In approve mutation success callback, trigger `startLinkVerification()` for the newly created Link
- [x] 5.2 In approve mutation success callback, trigger `startInitialLinkFeedRefresh()` for the newly created Link
- [x] 5.3 Add toast notifications for success/error states on approve/reject/delete

## 6. Integration & Verification

- [x] 6.1 Run `./gradlew build` to verify backend compiles
- [x] 6.2 Run `cd console && pnpm type-check` to verify frontend types
- [ ] 6.3 Start `./gradlew haloServer` and test submission flow from a theme form
- [ ] 6.4 Test Console approval flow: open detail, modify fields, select group, verify backlink, approve, confirm Link created
- [ ] 6.5 Test rate limiting and duplicate detection
- [ ] 6.6 Test form error回填 with query params
