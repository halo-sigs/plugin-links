## Context

`LinkRequest.getLinkDetail(String linkUrl)` in `run.halo.links.dto` is responsible for fetching a web page's title, description, and icon when an admin creates or edits a link in the console. It currently calls `Jsoup.connect(linkUrl).followRedirects(true).get()` without validating the URL. Because the Halo server executes this request, any internal address the admin provides is reachable from the server. This is a server-side request forgery (SSRF) vector.

The endpoint is a Console API (`/apis/console.api.link.halo.run/v1alpha1/links/-/detail`), so it requires authentication. The risk is therefore limited to authenticated administrators, but defense in depth is still warranted.

## Goals / Non-Goals

**Goals:**
- Block requests to private/reserved IP ranges before the connection is opened.
- Block requests to non-HTTP/HTTPS schemes.
- Limit and validate redirect hops so a public URL cannot redirect into a private network.
- Keep the change localized to `LinkRequest` and a small security utility package.

**Non-Goals:**
- Changing the API contract, response shape, or frontend behavior.
- Introducing a global URL firewall or proxy for all outbound traffic in the plugin.
- Solving DNS rebinding attacks (this requires a network-layer fix beyond the scope of a single plugin).
- Adding rate limiting or authentication (already handled by Halo's Console API layer).

## Decisions

### 1. Use `java.net.URL` + `java.net.InetAddress` for host validation
- **Rationale**: No external dependencies needed. Java 21's `InetAddress` resolves hostnames and can be checked against private ranges.
- **Alternative considered**: Apache Commons `InetAddressUtils` — rejected to avoid adding a dependency.

### 2. Resolve host before every redirect hop
- **Rationale**: A public domain might redirect to `http://192.168.1.1`. We must re-validate the resolved IP after each `Location` header.
- **Alternative considered**: Validate only the initial URL and trust Jsoup to follow redirects — rejected because it allows bypass via redirect.

### 3. Disable Jsoup `followRedirects`, handle redirects manually
- **Rationale**: Jsoup does not expose a hook to inspect each redirect target before connecting. Manual handling lets us validate every hop.
- **Trade-off**: Slightly more code, but full control over each request.

### 4. Maximum 3 redirects
- **Rationale**: Prevents redirect loops and limits attack surface. Three hops is generous for legitimate sites.

### 5. Reject IPv6 link-local and loopback in addition to IPv4 private ranges
- **Rationale**: Modern servers may bind to `::1` or `fe80::/10`; these are equally internal.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| DNS rebinding: attacker uses short TTL to switch from public to private IP after validation | Accept as known limitation; full mitigation requires OS/network-level fixes |
| Hostname resolution failure (e.g. DNS timeout) | Propagate the `IOException` as `ServerErrorException`, same as today |
| Overly strict filtering blocks legitimate private intranet links | Acceptable: this feature is for public friend links, not internal services |
| Performance: extra DNS lookup per redirect hop | Redirects are capped at 3; lookup is negligible compared to HTTP fetch |
