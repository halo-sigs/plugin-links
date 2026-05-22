package run.halo.links.security;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.web.server.ServerErrorException;

/**
 * Fetches remote HTTP(S) resources after validating URL targets against SSRF rules.
 */
public final class SafeUrlFetcher {

    public static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/58.0.3029.110 Safari/537.3";
    public static final int DEFAULT_TIMEOUT_MS = 10_000;
    public static final int DEFAULT_MAX_BODY_SIZE = 1024 * 1024 * 20;

    private SafeUrlFetcher() {
    }

    public static FetchResult fetch(String urlString, FetchOptions options) {
        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            throw new ServerErrorException("Invalid URL", e);
        }
        try {
            return fetch(url, options == null ? FetchOptions.html(urlString) : options);
        } catch (IOException e) {
            throw new ServerErrorException("Failed to fetch URL", e);
        }
    }

    private static FetchResult fetch(URL url, FetchOptions options) throws IOException {
        Connection.Response current = execute(url, options);
        int hopsRemaining = LinkSecurityUtils.getMaxRedirects();
        while (isRedirect(current.statusCode())) {
            if (hopsRemaining <= 0) {
                throw new ServerErrorException("Too many redirects",
                    new IllegalStateException("Exceeded maximum redirect limit of "
                        + LinkSecurityUtils.getMaxRedirects()));
            }
            hopsRemaining--;

            String location = current.header("Location");
            if (location == null || location.isBlank()) {
                throw new ServerErrorException("Redirect missing Location header",
                    new IllegalStateException("HTTP " + current.statusCode()
                        + " without Location"));
            }
            URL redirectUrl;
            try {
                redirectUrl = new URL(current.url(), location);
            } catch (MalformedURLException e) {
                throw new ServerErrorException("Invalid redirect URL: " + location, e);
            }
            current = execute(redirectUrl, options);
        }

        Document document = options.parseDocument ? current.parse() : null;
        return new FetchResult(current.url(), current.statusCode(), current.body(), document,
            current.header("ETag"), current.header("Last-Modified"));
    }

    private static Connection.Response execute(URL url, FetchOptions options)
        throws IOException {
        InetAddress validatedAddress;
        try {
            validatedAddress = LinkSecurityUtils.validateUrl(url);
        } catch (IllegalArgumentException e) {
            throw new ServerErrorException("URL blocked for security reasons", e);
        }

        String connectUrl = "http".equalsIgnoreCase(url.getProtocol())
            ? LinkSecurityUtils.toConnectUrl(url, validatedAddress)
            : url.toExternalForm();

        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Accept", options.accept);
        if (options.referer != null && !options.referer.isBlank()) {
            headers.put("Referer", options.referer);
        }
        if (options.etag != null && !options.etag.isBlank()) {
            headers.put("If-None-Match", options.etag);
        }
        if (options.lastModified != null && !options.lastModified.isBlank()) {
            headers.put("If-Modified-Since", options.lastModified);
        }
        if ("http".equalsIgnoreCase(url.getProtocol())) {
            headers.put("Host", url.getHost());
        }

        Connection.Response response = Jsoup.connect(connectUrl)
            .followRedirects(false)
            .ignoreHttpErrors(true)
            .ignoreContentType(options.ignoreContentType)
            .maxBodySize(options.maxBodySize + 1)
            .timeout(options.timeout)
            .headers(headers)
            .execute();
        return assertBodyWithinLimit(response, options.maxBodySize);
    }

    private static boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302
            || statusCode == 303 || statusCode == 307 || statusCode == 308;
    }

    private static Connection.Response assertBodyWithinLimit(Connection.Response response,
        int maxBodySize) {
        String contentLength = response.header("Content-Length");
        if (contentLength != null && !contentLength.isBlank()) {
            try {
                if (Long.parseLong(contentLength) > maxBodySize) {
                    throw new ServerErrorException("Response exceeds maximum size",
                        new IllegalStateException("Content-Length exceeds " + maxBodySize));
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed Content-Length and fall back to body length.
            }
        }
        String body = response.body();
        if (body != null && body.getBytes(StandardCharsets.UTF_8).length > maxBodySize) {
            throw new ServerErrorException("Response exceeds maximum size",
                new IllegalStateException("Body exceeds " + maxBodySize));
        }
        return response;
    }

    public record FetchResult(URL url, int statusCode, String body, Document document,
                              String etag, String lastModified) {
    }

    @Getter
    public static class FetchOptions {
        private final String accept;
        private final String referer;
        private final int timeout;
        private final int maxBodySize;
        private final boolean ignoreContentType;
        private final boolean parseDocument;
        private final String etag;
        private final String lastModified;

        private FetchOptions(String accept, String referer, int timeout, int maxBodySize,
            boolean ignoreContentType, boolean parseDocument, String etag, String lastModified) {
            this.accept = accept;
            this.referer = referer;
            this.timeout = timeout;
            this.maxBodySize = maxBodySize;
            this.ignoreContentType = ignoreContentType;
            this.parseDocument = parseDocument;
            this.etag = etag;
            this.lastModified = lastModified;
        }

        public static FetchOptions html(String referer) {
            return new FetchOptions("text/html,application/xhtml+xml,application/xml",
                referer, DEFAULT_TIMEOUT_MS, DEFAULT_MAX_BODY_SIZE, false, true, null, null);
        }

        public static FetchOptions feed(String referer, String etag, String lastModified) {
            return new FetchOptions("application/rss+xml,application/atom+xml,application/xml,text/xml",
                referer, DEFAULT_TIMEOUT_MS, DEFAULT_MAX_BODY_SIZE, true, false, etag,
                lastModified);
        }
    }
}
