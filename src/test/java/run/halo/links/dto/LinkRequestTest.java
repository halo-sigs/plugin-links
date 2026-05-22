package run.halo.links.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.server.ServerErrorException;
import run.halo.links.security.LinkSecurityUtils;

class LinkRequestTest {

    @Test
    void shouldBlockDirectAccessToPrivateIp() {
        assertThatThrownBy(() -> LinkRequest.getLinkDetail("http://192.168.1.1/"))
            .isInstanceOf(ServerErrorException.class)
            .hasMessageContaining("blocked");
    }

    @Test
    void shouldBlockDirectAccessToLoopback() {
        assertThatThrownBy(() -> LinkRequest.getLinkDetail("http://127.0.0.1:8090/actuator"))
            .isInstanceOf(ServerErrorException.class)
            .hasMessageContaining("blocked");
    }

    @Test
    void shouldBlockNonHttpScheme() {
        assertThatThrownBy(() -> LinkRequest.getLinkDetail("file:///etc/passwd"))
            .isInstanceOf(ServerErrorException.class)
            .hasMessageContaining("blocked");
    }

    @Test
    void shouldFetchPublicUrlSuccessfully() {
        LinkDetailDTO detail = LinkRequest.getLinkDetail("https://example.com");
        assertThat(detail.getTitle()).isNotBlank();
    }

    @Test
    void shouldConnectUsingValidatedIpForHttp() throws Exception {
        URL url = new URL("http://example.com");
        InetAddress mockAddr = mock(InetAddress.class);
        when(mockAddr.getHostAddress()).thenReturn("93.184.216.34");

        Connection mockConn = mock(Connection.class);
        Connection.Response mockResponse = mock(Connection.Response.class);
        when(mockResponse.statusCode()).thenReturn(200);
        Document doc = new Document("http://example.com");
        when(mockResponse.parse()).thenReturn(doc);
        when(mockResponse.header("Content-Length")).thenReturn(null);
        when(mockResponse.body()).thenReturn("");

        when(mockConn.followRedirects(false)).thenReturn(mockConn);
        when(mockConn.ignoreHttpErrors(true)).thenReturn(mockConn);
        when(mockConn.ignoreContentType(false)).thenReturn(mockConn);
        when(mockConn.maxBodySize(anyInt())).thenReturn(mockConn);
        when(mockConn.timeout(anyInt())).thenReturn(mockConn);
        when(mockConn.headers(anyMap())).thenReturn(mockConn);
        when(mockConn.execute()).thenReturn(mockResponse);

        try (MockedStatic<LinkSecurityUtils> security = mockStatic(LinkSecurityUtils.class);
             MockedStatic<Jsoup> jsoup = mockStatic(Jsoup.class)) {

            security.when(() -> LinkSecurityUtils.validateUrl(url)).thenReturn(mockAddr);
            security.when(() -> LinkSecurityUtils.toConnectUrl(url, mockAddr))
                .thenReturn("http://93.184.216.34");
            security.when(LinkSecurityUtils::getMaxRedirects).thenReturn(3);

            jsoup.when(() -> Jsoup.connect("http://93.184.216.34")).thenReturn(mockConn);

            LinkDetailDTO detail = LinkRequest.getLinkDetail("http://example.com");

            assertThat(detail).isNotNull();
            jsoup.verify(() -> Jsoup.connect("http://93.184.216.34"));
        }
    }
}
