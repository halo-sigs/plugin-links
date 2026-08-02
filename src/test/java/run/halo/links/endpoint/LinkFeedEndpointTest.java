package run.halo.links.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.links.rss.LinkFeedDiscoveryResult;
import run.halo.links.rss.LinkFeedHiddenStateRequest;
import run.halo.links.rss.LinkFeedHiddenStateResult;
import run.halo.links.rss.LinkFeedItemPage;
import run.halo.links.rss.LinkFeedItemQuery;
import run.halo.links.rss.LinkFeedItemStore;
import run.halo.links.rss.LinkFeedRefreshResult;
import run.halo.links.rss.LinkFeedService;
import run.halo.links.rss.LinkFeedStorageUnavailableException;

class LinkFeedEndpointTest {

    @Test
    void shouldReturnServiceUnavailableWhenRssStorageFails() {
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        when(itemStore.countUnread()).thenThrow(new LinkFeedStorageUnavailableException(
            "unavailable"));
        LinkFeedEndpoint endpoint = new LinkFeedEndpoint(null, null, itemStore, null, null);
        MockServerRequest request = request(HttpMethod.GET, "/rss/items/-/unread-summary");

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(503))
            .verifyComplete();
    }

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

    @Test
    void shouldDelegateManualRefreshToFeedService() {
        LinkFeedService feedService = mock(LinkFeedService.class);
        LinkFeedRefreshResult result = new LinkFeedRefreshResult();
        result.setLinkName("link-a");
        when(feedService.refresh("link-a")).thenReturn(Mono.just(result));
        LinkFeedEndpoint endpoint = new LinkFeedEndpoint(feedService, null, null, null, null);
        MockServerRequest request = requestWithPathVariable(HttpMethod.POST,
            "/links/link-a/rss/refresh", "name", "link-a");

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(200))
            .verifyComplete();

        verify(feedService).refresh("link-a");
    }

    @Test
    void shouldReturnUnreadSummary() {
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        when(itemStore.countUnread()).thenReturn(3L);
        when(itemStore.countUnreadByLinkName()).thenReturn(Map.of("link-a", 2L, "link-b", 1L));
        LinkFeedEndpoint endpoint = new LinkFeedEndpoint(null, null, itemStore, null, null);
        MockServerRequest request = request(HttpMethod.GET, "/rss/items/-/unread-summary");

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(200))
            .verifyComplete();

        verify(itemStore).countUnread();
        verify(itemStore).countUnreadByLinkName();
    }

    @Test
    void shouldListHiddenItemsWithCombinedFilters() {
        LinkFeedService feedService = mock(LinkFeedService.class);
        when(feedService.listItems(any())).thenReturn(new LinkFeedItemPage(List.of(), null, null,
            false));
        LinkFeedEndpoint endpoint = new LinkFeedEndpoint(feedService, null, null, null, null);
        MockServerRequest request = MockServerRequest.builder()
            .method(HttpMethod.GET)
            .uri(URI.create("/rss/items"))
            .queryParam("linkName", "link-a")
            .queryParam("hidden", "true")
            .queryParam("read", "false")
            .queryParam("favorite", "true")
            .queryParam("readLater", "true")
            .exchange(MockServerWebExchange.from(MockServerHttpRequest.get("/rss/items").build()))
            .build();

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(200))
            .verifyComplete();

        ArgumentCaptor<LinkFeedItemQuery> queryCaptor =
            ArgumentCaptor.forClass(LinkFeedItemQuery.class);
        verify(feedService).listItems(queryCaptor.capture());
        assertThat(queryCaptor.getValue()).satisfies(query -> {
            assertThat(query.getLinkName()).isEqualTo("link-a");
            assertThat(query.getHidden()).isTrue();
            assertThat(query.getRead()).isFalse();
            assertThat(query.getFavorite()).isTrue();
            assertThat(query.getReadLater()).isTrue();
        });
    }

    @Test
    void shouldReturnExactHiddenCount() {
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        when(itemStore.countHidden()).thenReturn(7L);
        LinkFeedEndpoint endpoint = new LinkFeedEndpoint(null, null, itemStore, null, null);
        MockServerRequest request = request(HttpMethod.GET, "/rss/items/-/hidden-count");

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(200))
            .verifyComplete();

        verify(itemStore).countHidden();
    }

    @Test
    void shouldDelegateBatchHiddenStateUpdate() {
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        when(itemStore.updateHidden(List.of("item-1", "missing"), true))
            .thenReturn(new LinkFeedHiddenStateResult(2, 1));
        LinkFeedEndpoint endpoint = new LinkFeedEndpoint(null, null, itemStore, null, null);
        LinkFeedHiddenStateRequest body = new LinkFeedHiddenStateRequest();
        body.setIds(List.of("item-1", "missing"));
        body.setHidden(true);
        MockServerRequest request = postRequest("/rss/items/-/hidden", body);

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(200))
            .verifyComplete();

        verify(itemStore).updateHidden(List.of("item-1", "missing"), true);
    }

    @Test
    void shouldRejectEmptyHiddenStateRequest() {
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        LinkFeedEndpoint endpoint = new LinkFeedEndpoint(null, null, itemStore, null, null);
        LinkFeedHiddenStateRequest body = new LinkFeedHiddenStateRequest();
        body.setIds(List.of());
        MockServerRequest request = postRequest("/rss/items/-/hidden", body);

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(400))
            .verifyComplete();

        verify(itemStore, never()).updateHidden(any(), anyBoolean());
    }

    @Test
    void shouldReturnServiceUnavailableWhenHiddenStateUpdateFails() {
        LinkFeedItemStore itemStore = mock(LinkFeedItemStore.class);
        when(itemStore.updateHidden(List.of("item-1"), true))
            .thenThrow(new LinkFeedStorageUnavailableException("unavailable"));
        LinkFeedEndpoint endpoint = new LinkFeedEndpoint(null, null, itemStore, null, null);
        LinkFeedHiddenStateRequest body = new LinkFeedHiddenStateRequest();
        body.setIds(List.of("item-1"));
        body.setHidden(true);
        MockServerRequest request = postRequest("/rss/items/-/hidden", body);

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(503))
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

    private static MockServerRequest request(HttpMethod method, String path) {
        var httpRequest = MockServerHttpRequest.method(method, path).build();
        var exchange = MockServerWebExchange.from(httpRequest);
        return MockServerRequest.builder()
            .method(method)
            .uri(URI.create(path))
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
