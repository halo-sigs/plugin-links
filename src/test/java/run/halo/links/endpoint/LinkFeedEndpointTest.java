package run.halo.links.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.links.rss.LinkFeedDiscoveryResult;
import run.halo.links.rss.LinkFeedItemStore;
import run.halo.links.rss.LinkFeedService;

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

    @Test
    void shouldRouteDiscoveryResultWithMultipleFeedUrls() {
        LinkFeedService feedService = mock(LinkFeedService.class);
        when(feedService.discover("https://example.com")).thenReturn(Mono.just(
            new LinkFeedDiscoveryResult(List.of("https://example.com/feed.xml",
                "https://example.com/comments.xml"))));
        LinkFeedEndpoint endpoint = new LinkFeedEndpoint(feedService, null, null, null, null);
        MockServerRequest request = requestWithQuery(HttpMethod.GET, "/rss/discovery", "url",
            "https://example.com");

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(200))
            .verifyComplete();
    }

    @Test
    void shouldReturnBadRequestWhenRefreshRejectsEmptyFeedUrls() {
        LinkFeedService feedService = mock(LinkFeedService.class);
        when(feedService.refresh("link-a")).thenReturn(Mono.error(new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "RSS feed URLs are required for this link.")));
        LinkFeedEndpoint endpoint = new LinkFeedEndpoint(feedService, null, null, null, null);
        MockServerRequest request = requestWithPathVariable(HttpMethod.POST,
            "/links/link-a/rss/refresh", "name", "link-a");

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(400))
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

    private static MockServerRequest requestWithQuery(HttpMethod method, String path,
        String queryParam, String value) {
        var httpRequest = MockServerHttpRequest.method(method, path).build();
        var exchange = MockServerWebExchange.from(httpRequest);
        return MockServerRequest.builder()
            .method(method)
            .uri(URI.create(path))
            .queryParam(queryParam, value)
            .exchange(exchange)
            .build();
    }

    private static MockServerRequest requestWithPathVariable(HttpMethod method, String path,
        String variableName, String value) {
        var httpRequest = MockServerHttpRequest.method(method, path).build();
        var exchange = MockServerWebExchange.from(httpRequest);
        return MockServerRequest.builder()
            .method(method)
            .uri(URI.create(path))
            .pathVariable(variableName, value)
            .exchange(exchange)
            .build();
    }
}
