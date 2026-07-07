package run.halo.links.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ServerErrorException;
import reactor.core.publisher.Mono;
import run.halo.links.dto.LinkDetailDTO;
import run.halo.links.dto.LinkRequest;

class LinkRequestTest {

    @AfterEach
    void tearDown() {
        SafeUrlFetcher.setExchangeFunctionForTesting(null);
    }

    @Test
    void shouldBlockDirectAccessToPrivateIp() {
        assertThatThrownBy(() -> LinkRequest.getLinkDetail("http://192.168.1.1/"))
            .isInstanceOf(ServerErrorException.class);
    }

    @Test
    void shouldBlockDirectAccessToLoopback() {
        assertThatThrownBy(() -> LinkRequest.getLinkDetail("http://127.0.0.1:8090/actuator"))
            .isInstanceOf(ServerErrorException.class);
    }

    @Test
    void shouldBlockNonHttpScheme() {
        assertThatThrownBy(() -> LinkRequest.getLinkDetail("file:///etc/passwd"))
            .isInstanceOf(ServerErrorException.class)
            .hasMessageContaining("blocked");
    }

    @Test
    void shouldFetchPublicUrlSuccessfully() {
        SafeUrlFetcher.setExchangeFunctionForTesting(request -> Mono.just(
            response(HttpStatus.OK, """
                <html>
                  <head>
                    <title>Example Site</title>
                    <meta name="description" content="Example description">
                    <link rel="icon" href="/favicon.ico">
                    <meta property="og:image" content="/preview.png">
                  </head>
                </html>
                """, builder -> builder.header(HttpHeaders.CONTENT_TYPE, "text/html"))));

        LinkDetailDTO detail = LinkRequest.getLinkDetail("https://example.com");

        assertThat(detail.getTitle()).isEqualTo("Example Site");
        assertThat(detail.getDescription()).isEqualTo("Example description");
        assertThat(detail.getIcon()).isEqualTo("https://example.com/favicon.ico");
        assertThat(detail.getImage()).isEqualTo("https://example.com/preview.png");
    }

    private static ClientResponse response(HttpStatus status, String body,
        Consumer<ClientResponse.Builder> customizer) {
        ClientResponse.Builder builder = ClientResponse.create(status);
        customizer.accept(builder);
        return builder.body(body).build();
    }
}
