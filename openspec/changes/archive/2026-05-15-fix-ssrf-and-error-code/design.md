## Context

The plugin provides a "Get Link Detail" feature that fetches external webpage metadata (title, description, favicon, og:image) by URL. The current implementation validates URLs in `LinkSecurityUtils.validateUrl()` using `InetAddress.getAllByName()` to block private IPs, then makes the actual HTTP request via Jsoup using the original URL string. This two-step process is vulnerable to DNS Rebinding because DNS resolution happens twice — once during validation and again during the actual connection.

Additionally, the current private IP detection misses IPv6 ULA (`fc00::/7`) addresses, and the endpoint returns HTTP 500 for validation failures instead of HTTP 400.

## Goals / Non-Goals

**Goals:**
- Prevent DNS Rebinding attacks by pinning the resolved IP to the connection
- Block IPv6 ULA addresses in addition to existing IPv4/IPv6 private ranges
- Return HTTP 400 for invalid or blocked URLs in the link detail endpoint
- Maintain backward compatibility with all existing public APIs

**Non-Goals:**
- Changing the frontend behavior or UI
- Adding rate limiting (out of scope for this change)
- Replacing Jsoup with another HTTP client

## Decisions

### Decision 1: Resolve hostname once and connect by IP with Host header
To prevent DNS Rebinding, we resolve the hostname to IP addresses once during validation, filter out private IPs, pick a public IP, and then connect via `Jsoup.connect("http://<ip>")` while manually injecting the original `Host` header. This ensures the actual TCP connection target is the already-validated IP.

**Alternative considered:** Using a custom `SocketFactory` or `ProxySelector`. Rejected because Jsoup does not expose low-level socket customization easily, and the Host-header approach is simpler and equally effective for this use case.

### Decision 2: Add explicit IPv6 ULA check
Java's `isSiteLocalAddress()` does not cover `fc00::/7`. We add an explicit byte-range check for ULA addresses in `isPrivateAddress()`.

**Alternative considered:** Using an external IP-range library. Rejected to avoid adding a new dependency for a single well-known range.

### Decision 3: Map validation exceptions to 400 in the endpoint layer
`LinkEndpoint.getLinkDetail()` currently wraps `IllegalArgumentException` in `ServerErrorException` (500). We change the endpoint to catch validation errors and return a proper 400 Bad Request response.

## Risks / Trade-offs

- **[Risk]** Connecting by IP with a manual Host header may break virtual-hosting for some CDNs that rely on SNI or Host-header routing. → **Mitigation**: The Host header is preserved from the original URL. Most modern services handle this correctly. This is an acceptable trade-off for the security gain.
- **[Risk]** IPv6-only hosts will fail if the resolved IP list contains only IPv6 addresses and the environment lacks IPv6 connectivity. → **Mitigation**: This is an existing limitation; the change does not worsen it.

## Migration Plan

No migration needed. This is a pure security hardening patch with no database or API contract changes. Deploy as a normal plugin update.
