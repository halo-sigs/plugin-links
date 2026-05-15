package run.halo.links.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerErrorException;
import reactor.test.StepVerifier;
import run.halo.links.dto.LinkDetailDTO;
import run.halo.links.dto.LinkRequest;

class LinkEndpointTest {

    private static MockServerRequest buildRequest(String queryString) {
        var httpRequest = MockServerHttpRequest.get("/links/-/detail" + queryString).build();
        var exchange = MockServerWebExchange.from(httpRequest);
        return MockServerRequest.builder()
            .uri(URI.create("/links/-/detail" + queryString))
            .exchange(exchange)
            .build();
    }

    @Test
    void shouldReturn400ForInvalidUrl() {
        LinkEndpoint endpoint = new LinkEndpoint(null);
        MockServerRequest request = buildRequest("?url=not-a-url");

        StepVerifier.create(
            endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(400))
            .verifyComplete();
    }

    @Test
    void shouldReturn400ForBlockedPrivateIp() {
        try (MockedStatic<LinkRequest> linkRequest = mockStatic(LinkRequest.class)) {
            linkRequest.when(() -> LinkRequest.getLinkDetail(any()))
                .thenThrow(new ServerErrorException("URL blocked for security reasons",
                    new IllegalArgumentException("private")));

            LinkEndpoint endpoint = new LinkEndpoint(null);
            MockServerRequest request = buildRequest("?url=http://192.168.1.1/");

            StepVerifier.create(
                endpoint.endpoint().route(request)
                    .flatMap(handler -> handler.handle(request)))
                .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(400))
                .verifyComplete();
        }
    }

    @Test
    void shouldReturn400ForNonHttpScheme() {
        try (MockedStatic<LinkRequest> linkRequest = mockStatic(LinkRequest.class)) {
            linkRequest.when(() -> LinkRequest.getLinkDetail(any()))
                .thenThrow(new ServerErrorException("URL blocked for security reasons",
                    new IllegalArgumentException("Only HTTP and HTTPS protocols are allowed")));

            LinkEndpoint endpoint = new LinkEndpoint(null);
            MockServerRequest request = buildRequest("?url=ftp://internal.ftp.server/resource");

            StepVerifier.create(
                endpoint.endpoint().route(request)
                    .flatMap(handler -> handler.handle(request)))
                .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(400))
                .verifyComplete();
        }
    }
}
