package run.halo.links.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

class LinkGroupEndpointTest {

    private static MockServerRequest buildRequest(HttpMethod method, String path) {
        var httpRequest = MockServerHttpRequest.method(method, path).build();
        var exchange = MockServerWebExchange.from(httpRequest);
        return MockServerRequest.builder()
            .method(method)
            .uri(URI.create(path))
            .exchange(exchange)
            .build();
    }

    @Test
    void shouldRouteSortGroupsUnderLinkGroupsEndpoint() {
        var endpoint = new LinkGroupEndpoint(null, null);
        var request = buildRequest(HttpMethod.POST, "/linkgroups/-/sort");

        StepVerifier.create(endpoint.endpoint().route(request))
            .assertNext(handler -> assertThat(handler).isNotNull())
            .verifyComplete();
    }

    @Test
    void shouldNotRouteDashedLinkGroupsSortPath() {
        var endpoint = new LinkGroupEndpoint(null, null);
        var request = buildRequest(HttpMethod.POST, "/link-groups/-/sort");

        StepVerifier.create(endpoint.endpoint().route(request))
            .verifyComplete();
    }
}
