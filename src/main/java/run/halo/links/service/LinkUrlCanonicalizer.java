package run.halo.links.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

/**
 * Produces conservative comparison keys for HTTP and HTTPS links.
 */
public final class LinkUrlCanonicalizer {

    private LinkUrlCanonicalizer() {
    }

    public static Optional<String> canonicalKey(String value) {
        if (StringUtils.isBlank(value)) {
            return Optional.empty();
        }
        try {
            var uri = new URI(value.trim());
            var scheme = uri.getScheme();
            var host = uri.getHost();
            if (scheme == null || host == null
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return Optional.empty();
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            host = host.toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if (("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443)) {
                port = -1;
            }
            var path = StringUtils.defaultIfEmpty(uri.getPath(), "/");
            var canonical = new URI(
                scheme,
                uri.getUserInfo(),
                host,
                port,
                path,
                uri.getQuery(),
                null
            );
            return Optional.of(canonical.toASCIIString());
        } catch (URISyntaxException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
