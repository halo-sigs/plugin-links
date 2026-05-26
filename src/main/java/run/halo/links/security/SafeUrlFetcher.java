package run.halo.links.security;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
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
    private static final int MAX_HEADER_LINE_SIZE = 8192;
    private static PinnedHttpsConnector pinnedHttpsConnector =
        SafeUrlFetcher::connectPinnedHttpsSocket;

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
        FetchResponse current = execute(url, options);
        int hopsRemaining = LinkSecurityUtils.getMaxRedirects();
        while (isRedirect(current.statusCode())) {
            if (hopsRemaining <= 0) {
                throw new ServerErrorException("Too many redirects",
                    new IllegalStateException("Exceeded maximum redirect limit of "
                        + LinkSecurityUtils.getMaxRedirects()));
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

    private static FetchResponse execute(URL url, FetchOptions options)
        throws IOException {
        InetAddress validatedAddress;
        try {
            validatedAddress = LinkSecurityUtils.validateUrl(url);
        } catch (IllegalArgumentException e) {
            throw new ServerErrorException("URL blocked for security reasons", e);
        }

        if ("https".equalsIgnoreCase(url.getProtocol())) {
            return executePinnedHttps(url, validatedAddress, options);
        }
        return executeHttp(url, validatedAddress, options);
    }

    private static FetchResponse executeHttp(URL url, InetAddress validatedAddress,
        FetchOptions options) throws IOException {
        String connectUrl = "http".equalsIgnoreCase(url.getProtocol())
            ? LinkSecurityUtils.toConnectUrl(url, validatedAddress)
            : url.toExternalForm();

        Map<String, String> headers = requestHeaders(url, options);

        Connection.Response response = Jsoup.connect(connectUrl)
            .followRedirects(false)
            .ignoreHttpErrors(true)
            .ignoreContentType(options.ignoreContentType)
            .maxBodySize(options.maxBodySize + 1)
            .timeout(options.timeout)
            .headers(headers)
            .execute();
        assertBodyWithinLimit(response, options.maxBodySize);
        return new FetchResponse(url, response.statusCode(), response.body(),
            response.header("ETag"), response.header("Last-Modified"),
            response.header("Location"));
    }

    private static FetchResponse executePinnedHttps(URL url, InetAddress validatedAddress,
        FetchOptions options) throws IOException {
        try (Socket socket = pinnedHttpsConnector.connect(url, validatedAddress, options.timeout)) {
            writeRequest(socket.getOutputStream(), url, options);
            return readResponse(socket.getInputStream(), url, options.maxBodySize);
        }
    }

    private static Socket connectPinnedHttpsSocket(URL url, InetAddress address, int timeout)
        throws IOException {
        int port = effectivePort(url);
        Socket rawSocket = new Socket();
        try {
            rawSocket.connect(new InetSocketAddress(address, port), timeout);
            rawSocket.setSoTimeout(timeout);
            SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket) sslSocketFactory
                .createSocket(rawSocket, url.getHost(), port, true);
            try {
                SSLParameters parameters = sslSocket.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                sslSocket.setSSLParameters(parameters);
                sslSocket.startHandshake();
                return sslSocket;
            } catch (IOException e) {
                sslSocket.close();
                throw e;
            }
        } catch (IOException e) {
            rawSocket.close();
            throw e;
        }
    }

    private static void writeRequest(OutputStream outputStream, URL url, FetchOptions options)
        throws IOException {
        String target = url.getFile();
        if (target == null || target.isBlank()) {
            target = "/";
        }
        StringBuilder request = new StringBuilder();
        request.append("GET ").append(target).append(" HTTP/1.1\r\n");
        requestHeaders(url, options).forEach((name, value) ->
            request.append(name).append(": ").append(value).append("\r\n"));
        request.append("Connection: close\r\n\r\n");
        outputStream.write(request.toString().getBytes(StandardCharsets.ISO_8859_1));
        outputStream.flush();
    }

    private static FetchResponse readResponse(InputStream inputStream, URL url, int maxBodySize)
        throws IOException {
        String statusLine = readAsciiLine(inputStream);
        if (statusLine == null || !statusLine.startsWith("HTTP/")) {
            throw new IOException("Invalid HTTP response status line");
        }
        String[] statusParts = statusLine.split(" ", 3);
        if (statusParts.length < 2) {
            throw new IOException("Invalid HTTP response status line");
        }
        int statusCode = Integer.parseInt(statusParts[1]);
        Map<String, List<String>> headers = readHeaders(inputStream);
        byte[] body = readBody(inputStream, headers, maxBodySize);
        String contentType = header(headers, "content-type");
        return new FetchResponse(url, statusCode, decodeBody(body, contentType),
            header(headers, "etag"), header(headers, "last-modified"),
            header(headers, "location"));
    }

    private static Map<String, List<String>> readHeaders(InputStream inputStream)
        throws IOException {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        String line;
        while ((line = readAsciiLine(inputStream)) != null && !line.isEmpty()) {
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String name = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(separator + 1).trim();
            headers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
        }
        return headers;
    }

    private static byte[] readBody(InputStream inputStream, Map<String, List<String>> headers,
        int maxBodySize) throws IOException {
        String transferEncoding = header(headers, "transfer-encoding");
        if (transferEncoding != null
            && transferEncoding.toLowerCase(Locale.ROOT).contains("chunked")) {
            return readChunkedBody(inputStream, maxBodySize);
        }
        String contentLength = header(headers, "content-length");
        if (contentLength != null && !contentLength.isBlank()) {
            try {
                long length = Long.parseLong(contentLength);
                if (length > maxBodySize) {
                    throw responseTooLarge("Content-Length exceeds " + maxBodySize);
                }
                return inputStream.readNBytes((int) length);
            } catch (NumberFormatException ignored) {
                // Ignore malformed Content-Length and fall back to reading until EOF.
            }
        }
        return readUntilEof(inputStream, maxBodySize);
    }

    private static byte[] readChunkedBody(InputStream inputStream, int maxBodySize)
        throws IOException {
        var body = new java.io.ByteArrayOutputStream();
        while (true) {
            String chunkHeader = readAsciiLine(inputStream);
            if (chunkHeader == null) {
                throw new IOException("Unexpected EOF in chunked body");
            }
            int separator = chunkHeader.indexOf(';');
            String sizeText = separator >= 0 ? chunkHeader.substring(0, separator) : chunkHeader;
            int size = Integer.parseInt(sizeText.trim(), 16);
            if (size == 0) {
                while (true) {
                    String trailer = readAsciiLine(inputStream);
                    if (trailer == null || trailer.isEmpty()) {
                        return body.toByteArray();
                    }
                }
            }
            appendWithinLimit(body, inputStream.readNBytes(size), maxBodySize);
            readAsciiLine(inputStream);
        }
    }

    private static byte[] readUntilEof(InputStream inputStream, int maxBodySize)
        throws IOException {
        var body = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            if (body.size() + read > maxBodySize) {
                throw responseTooLarge("Body exceeds " + maxBodySize);
            }
            body.write(buffer, 0, read);
        }
        return body.toByteArray();
    }

    private static void appendWithinLimit(java.io.ByteArrayOutputStream output, byte[] bytes,
        int maxBodySize) {
        if (output.size() + bytes.length > maxBodySize) {
            throw responseTooLarge("Body exceeds " + maxBodySize);
        }
        output.writeBytes(bytes);
    }

    private static String readAsciiLine(InputStream inputStream) throws IOException {
        var line = new java.io.ByteArrayOutputStream();
        int value;
        while ((value = inputStream.read()) != -1) {
            if (value == '\n') {
                break;
            }
            if (value != '\r') {
                line.write(value);
                if (line.size() > MAX_HEADER_LINE_SIZE) {
                    throw new IOException("HTTP header line is too large");
                }
            }
        }
        if (value == -1 && line.size() == 0) {
            return null;
        }
        return line.toString(StandardCharsets.ISO_8859_1);
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

    private static String header(Map<String, List<String>> headers, String name) {
        List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private static Map<String, String> requestHeaders(URL url, FetchOptions options) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Host", hostHeader(url));
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
        return headers;
    }

    private static String hostHeader(URL url) {
        int port = url.getPort();
        if (port == -1 || port == url.getDefaultPort()) {
            return url.getHost();
        }
        return url.getHost() + ":" + port;
    }

    private static int effectivePort(URL url) {
        return url.getPort() == -1 ? url.getDefaultPort() : url.getPort();
    }

    private static ServerErrorException responseTooLarge(String message) {
        return new ServerErrorException("Response exceeds maximum size",
            new IllegalStateException(message));
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

    record FetchResponse(URL url, int statusCode, String body, String etag, String lastModified,
                         String location) {
    }

    @FunctionalInterface
    interface PinnedHttpsConnector {
        Socket connect(URL url, InetAddress address, int timeout) throws IOException;
    }

    static void setPinnedHttpsConnectorForTesting(PinnedHttpsConnector connector) {
        pinnedHttpsConnector = connector == null
            ? SafeUrlFetcher::connectPinnedHttpsSocket
            : connector;
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

        public static FetchOptions verification(String referer, int maxBodySize) {
            return verification(referer, maxBodySize, DEFAULT_TIMEOUT_MS);
        }

        public static FetchOptions verification(String referer, int maxBodySize, int timeout) {
            return new FetchOptions("*/*", referer, timeout, maxBodySize, true, false, null,
                null);
        }

        public static FetchOptions verificationHtml(String referer, int maxBodySize) {
            return verificationHtml(referer, maxBodySize, DEFAULT_TIMEOUT_MS);
        }

        public static FetchOptions verificationHtml(String referer, int maxBodySize,
            int timeout) {
            return new FetchOptions("text/html,application/xhtml+xml,application/xml",
                referer, timeout, maxBodySize, true, true, null, null);
        }
    }
}
