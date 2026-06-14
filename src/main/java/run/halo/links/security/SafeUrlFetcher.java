package run.halo.links.security;

import io.netty.channel.ChannelOption;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.Getter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.server.ServerErrorException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import run.halo.app.infra.utils.HttpSecurityUtils;

/**
 * Fetches remote HTTP(S) resources using Halo's SSRF-safe HTTP client.
 */
public final class SafeUrlFetcher {

    public static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/58.0.3029.110 Safari/537.3";
    public static final int DEFAULT_TIMEOUT_MS = 10_000;
    public static final int DEFAULT_MAX_BODY_SIZE = 1024 * 1024 * 20;
    private static final int MAX_REDIRECTS = 3;
    private static volatile ExchangeFunction exchangeFunctionForTesting;

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
        } catch (ServerErrorException e) {
            throw e;
        } catch (Exception e) {
            throw new ServerErrorException("Failed to fetch URL", e);
        }
    }

    private static FetchResult fetch(URL url, FetchOptions options) {
        FetchResponse current = execute(url, options);
        int hopsRemaining = MAX_REDIRECTS;
        while (isRedirect(current.statusCode())) {
            if (hopsRemaining <= 0) {
                throw new ServerErrorException("Too many redirects",
                    new IllegalStateException("Exceeded maximum redirect limit of "
                        + MAX_REDIRECTS));
            }
            hopsRemaining--;

            String location = current.location();
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

        Document document = options.parseDocument && current.body() != null
            ? Jsoup.parse(current.body(), current.url().toExternalForm())
            : null;
        return new FetchResult(current.url(), current.statusCode(), current.body(), document,
            current.etag(), current.lastModified());
    }

    private static FetchResponse execute(URL url, FetchOptions options) {
        validateHttpUrl(url);
        URI uri = toUri(url);
        FetchResponse response = webClient(options).get()
            .uri(uri)
            .headers(headers -> requestHeaders(url, options).forEach(headers::set))
            .exchangeToMono(clientResponse -> toFetchResponse(url, options, clientResponse))
            .block();
        if (response == null) {
            throw new ServerErrorException("Failed to fetch URL",
                new IllegalStateException("Empty response"));
        }
        return response;
    }

    private static WebClient webClient(FetchOptions options) {
        WebClient.Builder builder = WebClient.builder()
            .filter(HttpSecurityUtils.maxResponseSizeFilter(options.maxBodySize));
        ExchangeFunction exchangeFunction = exchangeFunctionForTesting;
        if (exchangeFunction != null) {
            return builder.exchangeFunction(exchangeFunction).build();
        }
        HttpClient httpClient = HttpSecurityUtils.secureHttpClient()
            .responseTimeout(Duration.ofMillis(options.timeout))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, options.timeout);
        return builder.clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }

    private static Mono<FetchResponse> toFetchResponse(URL url, FetchOptions options,
        ClientResponse response) {
        HttpHeaders headers = response.headers().asHttpHeaders();
        return bodyBytes(response, options)
            .map(body -> new FetchResponse(url, response.statusCode().value(),
                decodeBody(body, headers.getFirst(HttpHeaders.CONTENT_TYPE)),
                headers.getFirst(HttpHeaders.ETAG), headers.getFirst(HttpHeaders.LAST_MODIFIED),
                headers.getFirst(HttpHeaders.LOCATION)));
    }

    private static Mono<byte[]> bodyBytes(ClientResponse response, FetchOptions options) {
        return DataBufferUtils.join(response.body(BodyExtractors.toDataBuffers()))
            .map(SafeUrlFetcher::readAndRelease)
            .defaultIfEmpty(new byte[0])
            .onErrorResume(error -> {
                if (!isResponseTooLarge(error)) {
                    return Mono.error(error);
                }
                if (options.allowOversizedBody) {
                    return Mono.just(new byte[0]);
                }
                return Mono.error(responseTooLarge(error.getMessage()));
            });
    }

    private static byte[] readAndRelease(DataBuffer dataBuffer) {
        try {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            return bytes;
        } finally {
            DataBufferUtils.release(dataBuffer);
        }
    }

    private static void validateHttpUrl(URL url) {
        String protocol = url.getProtocol().toLowerCase(Locale.ROOT);
        if (!"http".equals(protocol) && !"https".equals(protocol)) {
            throw new ServerErrorException("URL blocked for security reasons",
                new IllegalArgumentException("Only HTTP and HTTPS protocols are allowed: " + url));
        }
        if (url.getHost() == null || url.getHost().isBlank()) {
            throw new ServerErrorException("Invalid URL",
                new IllegalArgumentException("URL must have a host: " + url));
        }
    }

    private static URI toUri(URL url) {
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            throw new ServerErrorException("Invalid URL", e);
        }
    }

    private static String decodeBody(byte[] body, String contentType) {
        if (body == null || body.length == 0) {
            return "";
        }
        Charset charset = StandardCharsets.UTF_8;
        if (contentType != null) {
            for (String part : contentType.split(";")) {
                String trimmed = part.trim();
                if (trimmed.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                    try {
                        charset = Charset.forName(trimmed.substring("charset=".length()));
                    } catch (Exception ignored) {
                        charset = StandardCharsets.UTF_8;
                    }
                }
            }
        }
        return new String(body, charset);
    }

    private static Map<String, String> requestHeaders(URL url, FetchOptions options) {
        Map<String, String> headers = new HashMap<>();
        headers.put(HttpHeaders.HOST, hostHeader(url));
        headers.put(HttpHeaders.USER_AGENT, USER_AGENT);
        headers.put(HttpHeaders.ACCEPT, options.accept);
        if (options.referer != null && !options.referer.isBlank()) {
            headers.put(HttpHeaders.REFERER, options.referer);
        }
        if (options.etag != null && !options.etag.isBlank()) {
            headers.put(HttpHeaders.IF_NONE_MATCH, options.etag);
        }
        if (options.lastModified != null && !options.lastModified.isBlank()) {
            headers.put(HttpHeaders.IF_MODIFIED_SINCE, options.lastModified);
        }
        return headers;
    }

    private static String hostHeader(URL url) {
        int port = url.getPort();
        if (port == -1 || port == url.getDefaultPort()) {
            return url.getHost();
        }
        return url.getHost() + ":" + port;
    }

    private static ServerErrorException responseTooLarge(String message) {
        return new ServerErrorException("Response exceeds maximum size",
            new IllegalStateException(message));
    }

    private static boolean isResponseTooLarge(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof DataBufferLimitException) {
                return true;
            }
            if (current instanceof ServerErrorException serverErrorException
                && "Response exceeds maximum size".equals(serverErrorException.getReason())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302
            || statusCode == 303 || statusCode == 307 || statusCode == 308;
    }

    public record FetchResult(URL url, int statusCode, String body, Document document,
                              String etag, String lastModified) {
    }

    record FetchResponse(URL url, int statusCode, String body, String etag, String lastModified,
                         String location) {
    }

    static void setExchangeFunctionForTesting(ExchangeFunction exchangeFunction) {
        exchangeFunctionForTesting = exchangeFunction;
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
        private final boolean allowOversizedBody;

        private FetchOptions(String accept, String referer, int timeout, int maxBodySize,
            boolean ignoreContentType, boolean parseDocument, String etag, String lastModified,
            boolean allowOversizedBody) {
            this.accept = accept;
            this.referer = referer;
            this.timeout = timeout;
            this.maxBodySize = maxBodySize;
            this.ignoreContentType = ignoreContentType;
            this.parseDocument = parseDocument;
            this.etag = etag;
            this.lastModified = lastModified;
            this.allowOversizedBody = allowOversizedBody;
        }

        public static FetchOptions html(String referer) {
            return new FetchOptions("text/html,application/xhtml+xml,application/xml",
                referer, DEFAULT_TIMEOUT_MS, DEFAULT_MAX_BODY_SIZE, false, true, null, null,
                false);
        }

        public static FetchOptions feed(String referer, String etag, String lastModified) {
            return new FetchOptions("application/rss+xml,application/atom+xml,application/xml,"
                + "text/xml", referer, DEFAULT_TIMEOUT_MS, DEFAULT_MAX_BODY_SIZE, true, false,
                etag, lastModified, false);
        }

        public static FetchOptions verification(String referer, int maxBodySize) {
            return verification(referer, maxBodySize, DEFAULT_TIMEOUT_MS);
        }

        public static FetchOptions verification(String referer, int maxBodySize, int timeout) {
            // Reachability checks only need status and final URL, not an oversized body.
            return new FetchOptions("*/*", referer, timeout, maxBodySize, true, false, null,
                null, true);
        }

        public static FetchOptions verificationHtml(String referer, int maxBodySize) {
            return verificationHtml(referer, maxBodySize, DEFAULT_TIMEOUT_MS);
        }

        public static FetchOptions verificationHtml(String referer, int maxBodySize,
            int timeout) {
            return new FetchOptions("text/html,application/xhtml+xml,application/xml",
                referer, timeout, maxBodySize, true, true, null, null, false);
        }
    }
}
