## Why

`LinkRequest.getLinkDetail()` uses Jsoup to fetch external URLs when an admin adds a link in the console. The current implementation does not validate the target URL, allowing the server to access internal addresses (e.g. `http://localhost:8090/actuator`). While the endpoint requires authentication, a compromised admin account or inadvertent input could leak internal services. This is an SSRF defense-in-depth fix.

## What Changes

- Add a `LinkSecurityUtils` utility class in `run.halo.links.security` that:
  - Rejects URLs using non-HTTP/HTTPS schemes
  - Resolves the host and rejects private/reserved IP ranges: `127.0.0.0/8`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `169.254.0.0/16`
  - Rejects link-local IPv6 addresses (`fe80::/10`, `::1`)
- Add a `RedirectHandler` that:
  - Intercepts each redirect and re-validates the new host against the same IP filter
  - Limits the number of redirects to a maximum of 3
  - Limits redirect hops to HTTP/HTTPS only
- Update `LinkRequest.getLinkDetail()` to:
  - Validate the input URL against the above filters before connecting
  - Use Jsoup's `followRedirects(false)` and manually handle redirects with the new handler
  - Return a clear `ServerErrorException` when validation fails
- Update unit tests (or add them) to cover allowed and blocked URLs.

## Capabilities

### New Capabilities
- `link-detail-ssrf-filter`: Secure URL fetching for link detail extraction — validates input URL, blocks private/reserved addresses, limits redirects, and restricts protocols to HTTP/HTTPS.

### Modified Capabilities
- *(none — this is a defensive hardening change that does not alter any spec-level behavior or API contract)*

## Impact

- **Backend**: New `run.halo.links.security` package with `LinkSecurityUtils` and `RedirectHandler`; changes to `run.halo.links.dto.LinkRequest`
- **Frontend**: No changes
- **API**: No public API changes; validation failures produce the same `ServerErrorException` response shape
- **Dependencies**: No new external dependencies; uses standard `java.net.InetAddress`, `java.net.URL`, and `org.jsoup.Connection`
