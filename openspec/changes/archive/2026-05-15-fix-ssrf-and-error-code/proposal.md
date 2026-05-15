## Why

The security review identified critical SSRF vulnerabilities in the link detail fetching feature and incorrect HTTP status codes for client errors. These issues could allow attackers to bypass IP-based restrictions and probe internal networks, while also returning misleading 500 errors for bad client input.

## What Changes

- Fix DNS Rebinding vulnerability in `LinkSecurityUtils.validateUrl()` by ensuring the resolved IP is pinned during the actual HTTP connection
- Add IPv6 ULA (`fc00::/7`) address blocking to `LinkSecurityUtils.isPrivateAddress()`
- Fix `LinkEndpoint.getLinkDetail()` to return HTTP 400 (Bad Request) for invalid/blocked URLs instead of 500 (Internal Server Error)
- Add corresponding unit tests for the new security checks

## Capabilities

### New Capabilities
- `ssrf-protection`: Enhanced SSRF protection for external URL fetching with DNS rebinding resistance and IPv6 ULA coverage

### Modified Capabilities
- *(none — this is a security fix to existing implementation without changing spec-level behavior)*

## Impact

- **Backend**: `LinkSecurityUtils.java`, `LinkRequest.java`, `RedirectHandler.java`, `LinkEndpoint.java`
- **Tests**: `LinkSecurityUtilsTest.java`, `RedirectHandlerTest.java`, `LinkRequestTest.java`
- **No frontend changes required**
- **No API contract changes** — only security hardening and correct HTTP status codes
