package run.halo.links.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerErrorException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.links.dto.LinkRequest;
import run.halo.links.verification.LinkVerificationRequest;
import run.halo.links.verification.LinkVerificationService;
import run.halo.links.verification.LinkVerificationTriggerResult;

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
        LinkEndpoint endpoint = new LinkEndpoint(null, null);
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

            LinkEndpoint endpoint = new LinkEndpoint(null, null);
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

            LinkEndpoint endpoint = new LinkEndpoint(null, null);
            MockServerRequest request = buildRequest("?url=ftp://internal.ftp.server/resource");

            StepVerifier.create(
                endpoint.endpoint().route(request)
                    .flatMap(handler -> handler.handle(request)))
                .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(400))
                .verifyComplete();
        }
    }

    @Test
    void shouldTriggerSelectedLinkVerification() {
        LinkVerificationService verificationService = mock(LinkVerificationService.class);
        LinkVerificationTriggerResult result = new LinkVerificationTriggerResult();
        result.setAcceptedNames(List.of("link-a"));
        when(verificationService.verify(any())).thenReturn(Mono.just(result));
        LinkEndpoint endpoint = new LinkEndpoint(null, verificationService);
        LinkVerificationRequest body = new LinkVerificationRequest();
        body.setNames(List.of("link-a"));
        MockServerRequest request = postRequest("/links/-/verification/check", body);

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(202))
            .verifyComplete();

        verify(verificationService).verify(any(LinkVerificationRequest.class));
    }

    @Test
    void shouldTriggerAllLinksVerificationWithEmptyRequest() {
        LinkVerificationService verificationService = mock(LinkVerificationService.class);
        LinkVerificationTriggerResult result = new LinkVerificationTriggerResult();
        when(verificationService.verify(any())).thenReturn(Mono.just(result));
        LinkEndpoint endpoint = new LinkEndpoint(null, verificationService);
        MockServerRequest request = postRequest("/links/-/verification/check",
            new LinkVerificationRequest());

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(202))
            .verifyComplete();

        verify(verificationService).verify(any(LinkVerificationRequest.class));
    }

    private static MockServerRequest postRequest(String path, Object body) {
        var httpRequest = MockServerHttpRequest.post(path).build();
        var exchange = MockServerWebExchange.from(httpRequest);
        return MockServerRequest.builder()
            .method(HttpMethod.POST)
            .uri(URI.create(path))
            .exchange(exchange)
            .body(Mono.just(body));
    }
}
