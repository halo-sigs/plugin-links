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
    public static InetAddress validateUrl(String urlString) {
        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL: " + urlString, e);
        }
        return validateUrl(url);
    }

    /**
     * Validates that the given URL is safe to connect to.
     *
     * @param url the URL to validate
     * @return the validated public {@link InetAddress}
     * @throws IllegalArgumentException if the URL uses an invalid scheme
     *                                  or resolves to a private/reserved address
     */
    public static InetAddress validateUrl(URL url) {
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
            if (!isPrivateAddress(address)) {
                return address;
            }
        }
        throw new IllegalArgumentException(
            "Access to private/reserved address is not allowed");
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

        // fc00::/7 (IPv6 Unique Local Address / ULA) — not covered by isSiteLocalAddress
        if (address.getAddress().length == 16) {
            byte[] bytes = address.getAddress();
            if ((bytes[0] & 0xFE) == 0xFC) {
                return true;
            }
        }

        return false;
    }

    public static int getMaxRedirects() {
        return MAX_REDIRECTS;
    }

    /**
     * Builds a connection URL using the validated IP address while preserving
     * the original protocol, port, and path. IPv6 addresses are wrapped in brackets.
     *
     * @param originalUrl the original URL
     * @param address     the validated public IP address
     * @return a URL string suitable for direct IP connection
     */
    public static String toConnectUrl(URL originalUrl, InetAddress address) {
        StringBuilder sb = new StringBuilder();
        sb.append(originalUrl.getProtocol()).append("://");
        if (address instanceof java.net.Inet6Address) {
            sb.append('[').append(address.getHostAddress()).append(']');
        } else {
            sb.append(address.getHostAddress());
        }
        if (originalUrl.getPort() != -1) {
            sb.append(':').append(originalUrl.getPort());
        }
        sb.append(originalUrl.getFile());
        return sb.toString();
    }

}
