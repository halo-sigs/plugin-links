## 1. IPv6 ULA Blocking

- [x] 1.1 Add `fc00::/7` detection to `LinkSecurityUtils.isPrivateAddress()`
- [x] 1.2 Add unit test for IPv6 ULA blocking in `LinkSecurityUtilsTest`

## 2. DNS Rebinding Protection

- [x] 2.1 Refactor `LinkSecurityUtils.validateUrl()` to return the validated public `InetAddress` instead of void
- [x] 2.2 Update `LinkRequest.getLinkDetail()` to connect by validated IP with preserved Host header (HTTP only; HTTPS uses domain with validated IP)
- [x] 2.3 Update `RedirectHandler.followRedirects()` to validate each redirect target and connect by validated IP with Host header (HTTP only)
- [x] 2.4 Update `RedirectHandlerTest` to verify redirect connections use validated IPs
- [x] 2.5 Update `LinkRequestTest` to verify DNS rebinding resistance

## 3. Correct HTTP Status Codes

- [x] 3.1 Update `LinkEndpoint.getLinkDetail()` to catch validation failures and return HTTP 400 (Bad Request) instead of 500
- [x] 3.2 Add unit/integration test asserting 400 response for invalid URLs
- [x] 3.3 Add unit/integration test asserting 400 response for blocked private IPs

## 4. Build & Verification

- [x] 4.1 Add explicit `org.jsoup:jsoup` dependency to `build.gradle`
- [x] 4.2 Run `./gradlew test` and ensure all tests pass
- [x] 4.3 Run `./gradlew build` and verify the plugin compiles cleanly
