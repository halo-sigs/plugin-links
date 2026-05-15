package run.halo.links.security;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

/**
 * Utility class for validating URLs to prevent SSRF attacks.
 * Blocks private/reserved IP ranges and non-HTTP(S) schemes.
 */
public final class LinkSecurityUtils {

    private static final int MAX_REDIRECTS = 3;

    private LinkSecurityUtils() {
    }

    /**
     * Validates that the given URL string is safe to connect to.
     *
     * @param urlString the URL to validate
     * @throws IllegalArgumentException if the URL is malformed, uses an invalid scheme,
     *                                  or resolves to a private/reserved address
     */
    public static void validateUrl(String urlString) {
        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL: " + urlString, e);
        }
        validateUrl(url);
    }

    /**
     * Validates that the given URL is safe to connect to.
     *
     * @param url the URL to validate
     * @throws IllegalArgumentException if the URL uses an invalid scheme
     *                                  or resolves to a private/reserved address
     */
    public static void validateUrl(URL url) {
        String protocol = url.getProtocol().toLowerCase();
        if (!"http".equals(protocol) && !"https".equals(protocol)) {
            throw new IllegalArgumentException(
                "Only HTTP and HTTPS protocols are allowed: " + url);
        }

        String host = url.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL must have a host: " + url);
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(
                "Unable to resolve host: " + host, e);
        }

        for (InetAddress address : addresses) {
            if (isPrivateAddress(address)) {
                throw new IllegalArgumentException(
                    "Access to private/reserved address is not allowed: " + address.getHostAddress());
            }
        }
    }

    /**
     * Checks if the given IP address is private, loopback, link-local, or otherwise reserved.
     *
     * @param address the address to check
     * @return true if the address is private/reserved
     */
    public static boolean isPrivateAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isLinkLocalAddress()) {
            return true;
        }

        if (address.isSiteLocalAddress()) {
            return true;
        }

        // 169.254.0.0/16 (APIPA / IPv4 link-local) — not covered by isSiteLocalAddress
        if (address.getAddress().length == 4) {
            byte[] bytes = address.getAddress();
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            if (first == 169 && second == 254) {
                return true;
            }
        }

        return false;
    }

    public static int getMaxRedirects() {
        return MAX_REDIRECTS;
    }
}
