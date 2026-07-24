package run.halo.links.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.dto.LinkAiSettings;

@ExtendWith(MockitoExtension.class)
class LinkAiStatusEndpointTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    LinkAiSettingsFetcher settingsFetcher;

    @Test
    void shouldReturnDisabledStatusWhenAiIsNotEnabled() {
        when(settingsFetcher.fetch()).thenReturn(Mono.just(disabledSettings()));

        var endpoint = new LinkAiStatusEndpoint(client, settingsFetcher);
        var request = getRequest("/links/-/ai-status");

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(200))
            .verifyComplete();
    }

    @Test
    void shouldReturnNotFoundWhenCommentExtractionIsDisabled() {
        when(settingsFetcher.fetch()).thenReturn(Mono.just(disabledSettings()));

        var endpoint = new LinkAiStatusEndpoint(client, settingsFetcher);
        var request = getRequest("/links/-/recent-comments");

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(404))
            .verifyComplete();
    }

    @Test
    void shouldReturnRecentCommentsWhenCommentExtractionIsEnabled() {
        when(settingsFetcher.fetch()).thenReturn(Mono.just(enabledSettings()));
        when(client.listBy(any(), any(), any()))
            .thenReturn(Mono.just(new ListResult<>(1, 10, 0, java.util.List.of())));

        var endpoint = new LinkAiStatusEndpoint(client, settingsFetcher);
        var request = getRequest("/links/-/recent-comments");

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(200))
            .verifyComplete();
    }

    private static LinkAiSettings disabledSettings() {
        var settings = new LinkAiSettings();
        settings.setEnabled(false);
        settings.setCommentExtraction(null);
        return settings.normalized();
    }

    private static LinkAiSettings enabledSettings() {
        var settings = new LinkAiSettings();
        settings.setEnabled(true);
        var commentExtraction = new LinkAiSettings.CommentExtraction();
        commentExtraction.setEnabled(true);
        settings.setCommentExtraction(commentExtraction);
        return settings.normalized();
    }

    private static MockServerRequest getRequest(String path) {
        var httpRequest = MockServerHttpRequest.get(path).build();
        var exchange = MockServerWebExchange.from(httpRequest);
        return MockServerRequest.builder()
            .method(HttpMethod.GET)
            .uri(URI.create(path))
            .exchange(exchange)
            .build();
    }
}
