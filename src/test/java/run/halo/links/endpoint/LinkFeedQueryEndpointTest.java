package run.halo.links.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.links.rss.LinkFeedItemQuery;
import run.halo.links.service.LinkFeedPublicQueryService;
import run.halo.links.vo.LinkFeedItemPageVo;

class LinkFeedQueryEndpointTest {

    @Test
    void shouldListFeedItemsOnBoundedElastic() {
        LinkFeedPublicQueryService service = mock(LinkFeedPublicQueryService.class);
        AtomicBoolean invoked = new AtomicBoolean();
        AtomicReference<String> threadName = new AtomicReference<>();
        when(service.listFeeds(nullable(String.class), any(LinkFeedItemQuery.class)))
            .thenAnswer(invocation -> {
                invoked.set(true);
                threadName.set(Thread.currentThread().getName());
                return Mono.just(new LinkFeedItemPageVo(List.of(), null, null, false));
            });
        LinkFeedQueryEndpoint endpoint = new LinkFeedQueryEndpoint(service);
        MockServerRequest request = request(HttpMethod.GET, "/linkfeeds");

        var response = endpoint.endpoint().route(request)
            .flatMap(handler -> handler.handle(request));

        assertThat(invoked).isFalse();
        StepVerifier.create(response)
            .assertNext(result -> assertThat(result.statusCode().value()).isEqualTo(200))
            .verifyComplete();

        assertThat(invoked).isTrue();
        assertThat(threadName.get()).contains("boundedElastic");
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
}
