package run.halo.links.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.server.ServerErrorException;

class SafeUrlFetcherTest {

    @Test
    void shouldBlockPrivateFeedUrlBeforeConnecting() {
        assertThatThrownBy(() -> SafeUrlFetcher.fetch("http://127.0.0.1/feed.xml",
            SafeUrlFetcher.FetchOptions.feed("http://example.com", null, null)))
            .isInstanceOf(ServerErrorException.class)
            .hasMessageContaining("blocked");
    }

    @Test
    void shouldBlockRedirectToPrivateAddress() throws Exception {
        URL startUrl = new URL("http://example.com/feed.xml");
        InetAddress publicAddress = mock(InetAddress.class);
        when(publicAddress.getHostAddress()).thenReturn("93.184.216.34");

        Connection mockConn = mock(Connection.class);
        Connection.Response redirectResponse = mockResponse(302, "http://192.168.1.1/feed.xml");

        when(mockConn.followRedirects(false)).thenReturn(mockConn);
        when(mockConn.ignoreHttpErrors(true)).thenReturn(mockConn);
        when(mockConn.ignoreContentType(true)).thenReturn(mockConn);
        when(mockConn.maxBodySize(anyInt())).thenReturn(mockConn);
        when(mockConn.timeout(anyInt())).thenReturn(mockConn);
        when(mockConn.headers(anyMap())).thenReturn(mockConn);
        when(mockConn.execute()).thenReturn(redirectResponse);

        try (MockedStatic<LinkSecurityUtils> security = mockStatic(LinkSecurityUtils.class);
             MockedStatic<Jsoup> jsoup = mockStatic(Jsoup.class)) {
            security.when(() -> LinkSecurityUtils.validateUrl(any(URL.class)))
                .thenAnswer(invocation -> {
                    URL url = invocation.getArgument(0);
                    if (startUrl.equals(url)) {
                        return publicAddress;
                    }
                    throw new IllegalArgumentException("private");
                });
            security.when(() -> LinkSecurityUtils.toConnectUrl(startUrl, publicAddress))
                .thenReturn("http://93.184.216.34/feed.xml");
            security.when(LinkSecurityUtils::getMaxRedirects).thenReturn(3);
            jsoup.when(() -> Jsoup.connect("http://93.184.216.34/feed.xml"))
                .thenReturn(mockConn);

            assertThatThrownBy(() -> SafeUrlFetcher.fetch(startUrl.toExternalForm(),
                SafeUrlFetcher.FetchOptions.feed(startUrl.toExternalForm(), null, null)))
                .isInstanceOf(ServerErrorException.class)
                .hasMessageContaining("blocked");
        }
    }

    @Test
    void shouldRejectResponseOverMaximumBodySize() throws Exception {
        URL startUrl = new URL("http://example.com/feed.xml");
        InetAddress publicAddress = mock(InetAddress.class);
        when(publicAddress.getHostAddress()).thenReturn("93.184.216.34");

        Connection mockConn = mock(Connection.class);
        Connection.Response oversizedResponse = mockResponse(200, null);
        when(oversizedResponse.header("Content-Length")).thenReturn(
            String.valueOf(SafeUrlFetcher.DEFAULT_MAX_BODY_SIZE + 1L));

        when(mockConn.followRedirects(false)).thenReturn(mockConn);
        when(mockConn.ignoreHttpErrors(true)).thenReturn(mockConn);
        when(mockConn.ignoreContentType(true)).thenReturn(mockConn);
        when(mockConn.maxBodySize(anyInt())).thenReturn(mockConn);
        when(mockConn.timeout(anyInt())).thenReturn(mockConn);
        when(mockConn.headers(anyMap())).thenReturn(mockConn);
        when(mockConn.execute()).thenReturn(oversizedResponse);

        try (MockedStatic<LinkSecurityUtils> security = mockStatic(LinkSecurityUtils.class);
             MockedStatic<Jsoup> jsoup = mockStatic(Jsoup.class)) {
            security.when(() -> LinkSecurityUtils.validateUrl(startUrl)).thenReturn(publicAddress);
            security.when(() -> LinkSecurityUtils.toConnectUrl(startUrl, publicAddress))
                .thenReturn("http://93.184.216.34/feed.xml");
            security.when(LinkSecurityUtils::getMaxRedirects).thenReturn(3);
            jsoup.when(() -> Jsoup.connect("http://93.184.216.34/feed.xml"))
                .thenReturn(mockConn);

            assertThatThrownBy(() -> SafeUrlFetcher.fetch(startUrl.toExternalForm(),
                SafeUrlFetcher.FetchOptions.feed(startUrl.toExternalForm(), null, null)))
                .isInstanceOf(ServerErrorException.class)
                .hasMessageContaining("maximum size");
        }
    }

    @Test
    void shouldKeepVerificationStatusWhenResponseOverMaximumBodySize() throws Exception {
        URL startUrl = new URL("http://example.com");
        InetAddress publicAddress = mock(InetAddress.class);
        when(publicAddress.getHostAddress()).thenReturn("93.184.216.34");

        Connection mockConn = mock(Connection.class);
        Connection.Response oversizedResponse = mockResponse(200, null);
        when(oversizedResponse.header("Content-Length")).thenReturn("65537");

        when(mockConn.followRedirects(false)).thenReturn(mockConn);
        when(mockConn.ignoreHttpErrors(true)).thenReturn(mockConn);
        when(mockConn.ignoreContentType(true)).thenReturn(mockConn);
        when(mockConn.maxBodySize(anyInt())).thenReturn(mockConn);
        when(mockConn.timeout(anyInt())).thenReturn(mockConn);
        when(mockConn.headers(anyMap())).thenReturn(mockConn);
        when(mockConn.execute()).thenReturn(oversizedResponse);

        try (MockedStatic<LinkSecurityUtils> security = mockStatic(LinkSecurityUtils.class);
             MockedStatic<Jsoup> jsoup = mockStatic(Jsoup.class)) {
            security.when(() -> LinkSecurityUtils.validateUrl(startUrl)).thenReturn(publicAddress);
            security.when(() -> LinkSecurityUtils.toConnectUrl(startUrl, publicAddress))
                .thenReturn("http://93.184.216.34");
            security.when(LinkSecurityUtils::getMaxRedirects).thenReturn(3);
            jsoup.when(() -> Jsoup.connect("http://93.184.216.34"))
                .thenReturn(mockConn);

            SafeUrlFetcher.FetchResult result = SafeUrlFetcher.fetch(startUrl.toExternalForm(),
                SafeUrlFetcher.FetchOptions.verification(startUrl.toExternalForm(), 64 * 1024));

            assertThat(result.statusCode()).isEqualTo(200);
            assertThat(result.body()).isEmpty();
        }
    }

    @Test
    void shouldKeepHttpsVerificationStatusWhenResponseOverMaximumBodySize() throws Exception {
        URL startUrl = new URL("https://example.com");
        InetAddress validatedAddress = InetAddress.getByName("203.0.113.10");
        FakeSocket socket = new FakeSocket("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok");

        SafeUrlFetcher.setPinnedHttpsConnectorForTesting((url, address, timeout) -> socket);

        try (MockedStatic<LinkSecurityUtils> security = mockStatic(LinkSecurityUtils.class)) {
            security.when(() -> LinkSecurityUtils.validateUrl(startUrl))
                .thenReturn(validatedAddress);
            security.when(LinkSecurityUtils::getMaxRedirects).thenReturn(3);

            SafeUrlFetcher.FetchResult result = SafeUrlFetcher.fetch(startUrl.toExternalForm(),
                SafeUrlFetcher.FetchOptions.verification(startUrl.toExternalForm(), 1));

            assertThat(result.statusCode()).isEqualTo(200);
            assertThat(result.body()).isEmpty();
        } finally {
            SafeUrlFetcher.setPinnedHttpsConnectorForTesting(null);
        }
    }

    @Test
    void shouldConnectHttpsThroughValidatedAddress() throws Exception {
        URL startUrl = new URL("https://example.com/feed.xml");
        InetAddress validatedAddress = InetAddress.getByName("203.0.113.10");
        AtomicReference<URL> requestedUrl = new AtomicReference<>();
        AtomicReference<InetAddress> connectedAddress = new AtomicReference<>();
        FakeSocket socket = new FakeSocket("""
            HTTP/1.1 200 OK\r
            ETag: "feed-v1"\r
            \r
            <rss></rss>""");

        SafeUrlFetcher.setPinnedHttpsConnectorForTesting((url, address, timeout) -> {
            requestedUrl.set(url);
            connectedAddress.set(address);
            return socket;
        });

        try (MockedStatic<LinkSecurityUtils> security = mockStatic(LinkSecurityUtils.class)) {
            security.when(() -> LinkSecurityUtils.validateUrl(startUrl))
                .thenReturn(validatedAddress);
            security.when(LinkSecurityUtils::getMaxRedirects).thenReturn(3);

            SafeUrlFetcher.FetchResult result = SafeUrlFetcher.fetch(startUrl.toExternalForm(),
                SafeUrlFetcher.FetchOptions.feed(startUrl.toExternalForm(), null, null));

            assertThat(result.statusCode()).isEqualTo(200);
            assertThat(result.body()).isEqualTo("<rss></rss>");
            assertThat(requestedUrl).hasValue(startUrl);
            assertThat(connectedAddress).hasValue(validatedAddress);
            assertThat(socket.output()).contains("Host: example.com");
        } finally {
            SafeUrlFetcher.setPinnedHttpsConnectorForTesting(null);
        }
    }

    private static Connection.Response mockResponse(int statusCode, String location)
        throws IOException {
        Connection.Response response = mock(Response.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.header("Location")).thenReturn(location);
        when(response.header("Content-Length")).thenReturn(null);
        when(response.body()).thenReturn("");
        when(response.url()).thenReturn(new URL("http://example.com/feed.xml"));
        return response;
    }

    private interface Response extends Connection.Response {
    }

    private static final class FakeSocket extends Socket {
        private final ByteArrayInputStream input;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        private FakeSocket(String response) {
            this.input = new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public InputStream getInputStream() {
            return input;
        }

        @Override
        public OutputStream getOutputStream() {
            return output;
        }

        private String output() {
            return output.toString(StandardCharsets.UTF_8);
        }
    }
}
