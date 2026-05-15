## 1. Create security utility package

- [x] 1.1 Create `src/main/java/run/halo/links/security/LinkSecurityUtils.java` with static helpers to validate URL scheme (http/https only) and resolved host IP against private/reserved ranges (127.0.0.0/8, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 169.254.0.0/16, ::1, fe80::/10)
- [x] 1.2 Add `isPrivateAddress(InetAddress)` helper using `InetAddress` methods and range checks

## 2. Implement manual redirect handling

- [x] 2.1 Create `src/main/java/run/halo/links/security/RedirectHandler.java` that:
  - Takes the initial `Connection.Response` and extracts `Location` header
  - Validates the redirect target URL with `LinkSecurityUtils`
  - Limits total redirects to 3
  - Rebuilds a new Jsoup connection for each hop with the same headers/timeout settings
- [x] 2.2 Ensure the handler throws `ServerErrorException` on blocked redirect or exceeded hop limit

## 3. Update LinkRequest to use SSRF filters

- [x] 3.1 Refactor `LinkRequest.getLinkDetail(String linkUrl)` to:
  - Parse the input URL with `java.net.URL`
  - Validate scheme and host via `LinkSecurityUtils`
  - Open initial connection with `followRedirects(false)`
  - If response is a redirect, delegate to `RedirectHandler`
  - Return `Document` after final hop
  - Preserve existing headers, timeout (10s), and max body size (20MB)
- [x] 3.2 Keep the same exception handling shape (`ServerErrorException` on IO or validation failure)

## 4. Add unit tests

- [x] 4.1 Create `src/test/java/run/halo/links/security/LinkSecurityUtilsTest.java` covering:
  - Blocked: 127.0.0.1, 10.0.0.1, 172.16.0.1, 192.168.1.1, ::1, fe80::1
  - Allowed: example.com, 8.8.8.8
  - Blocked schemes: file://, ftp://
- [x] 4.2 Create `src/test/java/run/halo/links/security/RedirectHandlerTest.java` covering:
  - Redirect chain that ends at a private IP is blocked
  - More than 3 redirects is blocked
  - Successful fetch of a public URL still works
- [x] 4.3 Verify `./gradlew test` passes

## 5. Verify and finalize

- [x] 5.1 Run `./gradlew build` to ensure the plugin compiles and tests pass
- [x] 5.2 Review `LinkRequest.java` for code style compliance (100-char line length, 4-space indent)
