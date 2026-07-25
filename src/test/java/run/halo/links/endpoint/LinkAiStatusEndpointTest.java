package run.halo.links.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.EntityResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.dto.LinkAiFeatureStatus;
import run.halo.links.dto.LinkAiSettings;
import run.halo.links.dto.LinkApplicationSettings;
import run.halo.links.service.ai.LinkAiService;

@ExtendWith(MockitoExtension.class)
class LinkAiStatusEndpointTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    LinkAiSettingsFetcher settingsFetcher;

    @Mock
    LinkApplicationSettingsFetcher applicationSettingsFetcher;

    @Mock
    ObjectProvider<LinkAiService> aiServiceProvider;

    @Mock
    LinkAiService aiService;

    @Test
    void shouldReturnDisabledStatusWhenAiIsNotEnabled() {
        when(settingsFetcher.fetch()).thenReturn(Mono.just(disabledSettings()));
        when(applicationSettingsFetcher.fetch())
            .thenReturn(Mono.just(LinkApplicationSettings.defaults()));

        var endpoint = endpoint();
        var request = getRequest("/links/-/ai-status");

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(200))
            .verifyComplete();
    }

    @Test
    void shouldReturnNotFoundWhenCommentExtractionIsDisabled() {
        when(settingsFetcher.fetch()).thenReturn(Mono.just(disabledSettings()));

        var endpoint = endpoint();
        var request = getRequest("/links/-/recent-comments");

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(404))
            .verifyComplete();
    }

    @Test
    void shouldReturnRecentCommentsWhenCommentExtractionIsEnabled() {
        when(settingsFetcher.fetch()).thenReturn(Mono.just(enabledSettings()));
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);
        when(aiService.isOperational(null)).thenReturn(Mono.just(true));
        when(client.listBy(any(), any(), any()))
            .thenReturn(Mono.just(new ListResult<>(1, 10, 0, java.util.List.of())));

        var endpoint = endpoint();
        var request = getRequest("/links/-/recent-comments");

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(200))
            .verifyComplete();
    }

    @Test
    void shouldDistinguishConfiguredRecognitionFromOperationalModel() {
        when(settingsFetcher.fetch()).thenReturn(Mono.just(disabledSettings()));
        when(applicationSettingsFetcher.fetch())
            .thenReturn(Mono.just(recognitionSettings()));
        when(aiServiceProvider.getIfAvailable()).thenReturn(aiService);
        when(aiService.isOperational(null)).thenReturn(Mono.just(false));
        when(aiService.isOperational("model-a")).thenReturn(Mono.just(false));
        var endpoint = endpoint();
        var request = getRequest("/links/-/ai-status");

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> {
                assertThat(response).isInstanceOf(EntityResponse.class);
                var status = (LinkAiFeatureStatus) ((EntityResponse<?>) response).entity();
                assertThat(status.available()).isTrue();
                assertThat(status.operational()).isFalse();
                assertThat(status.commentApplicationRecognitionEnabled()).isTrue();
                assertThat(status.commentApplicationRecognitionOperational()).isFalse();
                assertThat(status.commentApplicationRecognitionModelName()).isEqualTo("model-a");
            })
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

    private static LinkApplicationSettings recognitionSettings() {
        var settings = new LinkApplicationSettings();
        settings.setEnabled(true);
        var recognition = new LinkApplicationSettings.CommentRecognition();
        recognition.setEnabled(true);
        recognition.setModelName("model-a");
        var source = new LinkApplicationSettings.RecognitionSource();
        source.setType(LinkApplicationSettings.SourceType.LINKS);
        recognition.setSources(java.util.List.of(source));
        settings.setCommentRecognition(recognition);
        return settings.normalized();
    }

    private LinkAiStatusEndpoint endpoint() {
        return new LinkAiStatusEndpoint(client, settingsFetcher, applicationSettingsFetcher,
            aiServiceProvider);
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
