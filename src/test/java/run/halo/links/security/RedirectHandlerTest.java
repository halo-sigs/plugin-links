package run.halo.links.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.server.ServerErrorException;

class RedirectHandlerTest {

    private static final Map<String, String> HEADERS = Map.of("User-Agent", "test");
    private static final int TIMEOUT = 10000;
    private static final int MAX_BODY = 1024 * 1024 * 20;

    @Test
    void shouldBlockRedirectToPrivateIp() throws IOException {
        Connection.Response firstResponse = mockResponse(302, "http://192.168.1.1/secret");

        try (MockedStatic<LinkSecurityUtils> security = mockStatic(LinkSecurityUtils.class)) {
            security.when(() -> LinkSecurityUtils.validateUrl(any(java.net.URL.class)))
                .thenThrow(new IllegalArgumentException("private"));
            security.when(LinkSecurityUtils::getMaxRedirects).thenReturn(3);

            RedirectHandler handler = new RedirectHandler(HEADERS, TIMEOUT, MAX_BODY);

            assertThatThrownBy(() -> handler.followRedirects(firstResponse))
                .isInstanceOf(ServerErrorException.class)
                .hasMessageContaining("Redirect blocked");
        }
    }

    @Test
    void shouldBlockTooManyRedirects() throws IOException {
        Connection.Response response = mockResponse(302, "http://example.com/next");

        try (MockedStatic<Jsoup> jsoup = mockStatic(Jsoup.class);
             MockedStatic<LinkSecurityUtils> security = mockStatic(LinkSecurityUtils.class)) {
            Connection mockConn = mock(Connection.class);
            when(mockConn.followRedirects(false)).thenReturn(mockConn);
            when(mockConn.timeout(anyInt())).thenReturn(mockConn);
            when(mockConn.maxBodySize(anyInt())).thenReturn(mockConn);
            when(mockConn.headers(anyMap())).thenReturn(mockConn);
            when(mockConn.execute()).thenReturn(response);

            jsoup.when(() -> Jsoup.connect(anyString())).thenReturn(mockConn);

            InetAddress mockAddr = mock(InetAddress.class);
            when(mockAddr.getHostAddress()).thenReturn("1.2.3.4");
            security.when(() -> LinkSecurityUtils.validateUrl(any(java.net.URL.class)))
                .thenReturn(mockAddr);
            security.when(() -> LinkSecurityUtils.toConnectUrl(
                    any(java.net.URL.class), any(InetAddress.class)))
                .thenReturn("http://1.2.3.4/next");
            security.when(LinkSecurityUtils::getMaxRedirects).thenReturn(3);

            RedirectHandler handler = new RedirectHandler(HEADERS, TIMEOUT, MAX_BODY);

            assertThatThrownBy(() -> handler.followRedirects(response))
                .isInstanceOf(ServerErrorException.class)
                .hasMessageContaining("Too many redirects");
        }
    }

    @Test
    void shouldFollowValidRedirectsAndReturnDocument() throws IOException {
        Connection.Response redirectResponse =
            mockResponse(302, "https://example.com/final");
        Document expectedDoc = new Document("https://example.com/final");
        Connection.Response finalResponse = mock(Response.class);
        when(finalResponse.statusCode()).thenReturn(200);
        when(finalResponse.parse()).thenReturn(expectedDoc);

        InetAddress mockAddr = mock(InetAddress.class);
        when(mockAddr.getHostAddress()).thenReturn("93.184.216.34");

        try (MockedStatic<Jsoup> jsoup = mockStatic(Jsoup.class);
             MockedStatic<LinkSecurityUtils> security = mockStatic(LinkSecurityUtils.class)) {
            Connection mockConn = mock(Connection.class);
            when(mockConn.followRedirects(false)).thenReturn(mockConn);
            when(mockConn.timeout(anyInt())).thenReturn(mockConn);
            when(mockConn.maxBodySize(anyInt())).thenReturn(mockConn);
            when(mockConn.headers(anyMap())).thenReturn(mockConn);
            when(mockConn.execute()).thenReturn(finalResponse);

            jsoup.when(() -> Jsoup.connect(anyString())).thenReturn(mockConn);
            security.when(() -> LinkSecurityUtils.validateUrl(any(java.net.URL.class)))
                .thenReturn(mockAddr);
            security.when(() -> LinkSecurityUtils.toConnectUrl(
                    any(java.net.URL.class), eq(mockAddr)))
                .thenReturn("https://93.184.216.34/final");
            security.when(LinkSecurityUtils::getMaxRedirects).thenReturn(3);

            RedirectHandler handler = new RedirectHandler(HEADERS, TIMEOUT, MAX_BODY);
            Document result = handler.followRedirects(redirectResponse);

            assertThat(result).isEqualTo(expectedDoc);
        }
    }

    @Test
    void shouldBlockSchemeDowngradeInRedirect() throws IOException {
        Connection.Response firstResponse = mockResponse(302, "ftp://evil.com/file");

        try (MockedStatic<LinkSecurityUtils> security = mockStatic(LinkSecurityUtils.class)) {
            security.when(() -> LinkSecurityUtils.validateUrl(any(java.net.URL.class)))
                .thenThrow(new IllegalArgumentException("scheme"));
            security.when(LinkSecurityUtils::getMaxRedirects).thenReturn(3);

            RedirectHandler handler = new RedirectHandler(HEADERS, TIMEOUT, MAX_BODY);

            assertThatThrownBy(() -> handler.followRedirects(firstResponse))
                .isInstanceOf(ServerErrorException.class)
                .hasMessageContaining("Redirect blocked");
        }
    }

    private Connection.Response mockResponse(int statusCode, String location)
        throws IOException {
        Connection.Response response = mock(Response.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.header("Location")).thenReturn(location);
        when(response.url()).thenReturn(new java.net.URL("https://example.com/start"));
        return response;
    }

    // Helper interface to avoid mocking the full Connection.Response
    private interface Response extends Connection.Response {
    }
}
