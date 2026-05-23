package run.halo.links.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;
import run.halo.links.rss.LinkFeedItemStore;

class LinkFeedEndpointTest {

    @Test
    void shouldReturnNotFoundWhenFavoriteItemIsMissing() {
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        when(itemStore.updateFavorite("missing", true)).thenReturn(false);
        LinkFeedEndpoint endpoint = new LinkFeedEndpoint(null, null, itemStore, null, null);
        MockServerRequest request = buildRequest(HttpMethod.POST,
            "/rss/items/missing/favorite", "missing", "favorite");

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(404))
            .verifyComplete();
    }

    @Test
    void shouldReturnNotFoundWhenReadLaterItemIsMissing() {
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        when(itemStore.updateReadLater("missing", true)).thenReturn(false);
        LinkFeedEndpoint endpoint = new LinkFeedEndpoint(null, null, itemStore, null, null);
        MockServerRequest request = buildRequest(HttpMethod.POST,
            "/rss/items/missing/read-later", "missing", "readLater");

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(404))
            .verifyComplete();
    }

    private static MockServerRequest buildRequest(HttpMethod method, String path, String id,
        String queryParam) {
        var httpRequest = MockServerHttpRequest.method(method, path).build();
        var exchange = MockServerWebExchange.from(httpRequest);
        return MockServerRequest.builder()
            .method(method)
            .uri(URI.create(path))
            .queryParam(queryParam, "true")
            .pathVariable("id", id)
            .exchange(exchange)
            .build();
    }
}
