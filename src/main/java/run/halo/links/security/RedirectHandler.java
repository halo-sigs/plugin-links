package run.halo.links.security;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.web.server.ServerErrorException;

/**
 * Handles HTTP redirects with SSRF validation.
 * Validates each redirect target before following, limits total hops, and
 * rebuilds connections with consistent settings.
 */
public class RedirectHandler {

    private final Map<String, String> headers;
    private final int timeout;
    private final int maxBodySize;
    private int hopsRemaining;

    public RedirectHandler(Map<String, String> headers, int timeout, int maxBodySize) {
        this.headers = headers;
        this.timeout = timeout;
        this.maxBodySize = maxBodySize;
        this.hopsRemaining = LinkSecurityUtils.getMaxRedirects();
    }

    /**
     * Follows redirects starting from the given response until a non-redirect
     * response is received or the hop limit is exceeded.
     *
     * @param response the initial Jsoup connection response
     * @return the final Document
     * @throws ServerErrorException if a redirect is blocked or hop limit exceeded
     * @throws IOException          on connection errors
     */
    public Document followRedirects(Connection.Response response) throws IOException {
        Connection.Response current = response;
        while (isRedirect(current.statusCode())) {
            if (hopsRemaining <= 0) {
                throw new ServerErrorException(
                    "Too many redirects", new IllegalStateException(
                        "Exceeded maximum redirect limit of "
                            + LinkSecurityUtils.getMaxRedirects()));
            }
            hopsRemaining--;

            String location = current.header("Location");
            if (location == null || location.isBlank()) {
                throw new ServerErrorException(
                    "Redirect missing Location header",
                    new IllegalStateException(
                        "HTTP " + current.statusCode() + " without Location"));
            }

            URL currentUrl = current.url();
            URL redirectUrl;
            try {
                redirectUrl = new URL(currentUrl, location);
            } catch (MalformedURLException e) {
                throw new ServerErrorException(
                    "Invalid redirect URL: " + location, e);
            }

            InetAddress validatedAddress;
            try {
                validatedAddress = LinkSecurityUtils.validateUrl(redirectUrl);
            } catch (IllegalArgumentException e) {
                throw new ServerErrorException(
                    "Redirect blocked for security reasons", e);
            }

            String connectUrl = "http".equalsIgnoreCase(redirectUrl.getProtocol())
                ? LinkSecurityUtils.toConnectUrl(redirectUrl, validatedAddress)
                : redirectUrl.toExternalForm();

            Map<String, String> requestHeaders = new HashMap<>(headers);
            if ("http".equalsIgnoreCase(redirectUrl.getProtocol())) {
                requestHeaders.put("Host", redirectUrl.getHost());
            }

            current = Jsoup.connect(connectUrl)
                .followRedirects(false)
                .timeout(timeout)
                .maxBodySize(maxBodySize)
                .headers(requestHeaders)
                .execute();
        }
        return current.parse();
    }

    private boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302
            || statusCode == 303 || statusCode == 307 || statusCode == 308;
    }
}
