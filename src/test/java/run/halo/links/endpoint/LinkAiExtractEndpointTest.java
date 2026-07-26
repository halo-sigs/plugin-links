package run.halo.links.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
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
import run.halo.links.dto.LinkAiSettings;
import run.halo.links.dto.LinkCommentExtractionResult;
import run.halo.links.dto.LinkCommentExtractionRequest;
import run.halo.links.service.ai.LinkAiService;

@ExtendWith(MockitoExtension.class)
class LinkAiExtractEndpointTest {

    @Mock
    LinkAiService aiService;

    @Mock
    LinkAiSettingsFetcher settingsFetcher;

    @Test
    void shouldUseConfiguredLanguageModel() {
        when(settingsFetcher.fetch()).thenReturn(Mono.just(settings("model-a")));
        when(aiService.extract("站点：https://halo.run", "model-a"))
            .thenReturn(Mono.just(extractionResult()));

        var endpoint = new LinkAiExtractEndpoint(aiService, settingsFetcher);
        var body = new LinkCommentExtractionRequest();
        body.setContent("站点：https://halo.run");
        var request = postRequest("/links/-/extract-from-comment", body);

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(200))
            .verifyComplete();

        verify(aiService).extract("站点：https://halo.run", "model-a");
    }

    @Test
    void shouldReturnNotFoundWhenCommentExtractionDisabled() {
        when(settingsFetcher.fetch()).thenReturn(Mono.just(disabledSettings()));

        var endpoint = new LinkAiExtractEndpoint(aiService, settingsFetcher);
        var body = new LinkCommentExtractionRequest();
        body.setContent("站点：https://halo.run");
        var request = postRequest("/links/-/extract-from-comment", body);

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(404))
            .verifyComplete();
    }

    @Test
    void shouldReturnBadRequestWhenContentIsBlank() {
        when(settingsFetcher.fetch()).thenReturn(Mono.just(settings(null)));

        var endpoint = new LinkAiExtractEndpoint(aiService, settingsFetcher);
        var body = new LinkCommentExtractionRequest();
        body.setContent("   ");
        var request = postRequest("/links/-/extract-from-comment", body);

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(400))
            .verifyComplete();
    }

    @Test
    void shouldReturnBadRequestWhenBodyIsMissing() {
        var endpoint = new LinkAiExtractEndpoint(aiService, settingsFetcher);
        var request = postRequest("/links/-/extract-from-comment", null);

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(400))
            .verifyComplete();
    }

    @Test
    void shouldUseDefaultLanguageModelWhenModelNameIsNull() {
        when(settingsFetcher.fetch()).thenReturn(Mono.just(settings(null)));
        when(aiService.extract("站点：https://halo.run", null))
            .thenReturn(Mono.just(extractionResult()));

        var endpoint = new LinkAiExtractEndpoint(aiService, settingsFetcher);
        var body = new LinkCommentExtractionRequest();
        body.setContent("站点：https://halo.run");
        var request = postRequest("/links/-/extract-from-comment", body);

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(200))
            .verifyComplete();

        verify(aiService).extract("站点：https://halo.run", null);
    }

    @Test
    void shouldReturnBadGatewayWhenAiServiceFails() {
        when(settingsFetcher.fetch()).thenReturn(Mono.just(settings(null)));
        when(aiService.extract("站点：https://halo.run", null))
            .thenReturn(Mono.error(new IllegalStateException("AI service down")));

        var endpoint = new LinkAiExtractEndpoint(aiService, settingsFetcher);
        var body = new LinkCommentExtractionRequest();
        body.setContent("站点：https://halo.run");
        var request = postRequest("/links/-/extract-from-comment", body);

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(502))
            .verifyComplete();
    }

    private static LinkAiSettings disabledSettings() {
        var settings = new LinkAiSettings();
        settings.setEnabled(false);
        settings.setCommentExtraction(null);
        return settings.normalized();
    }

    private static LinkAiSettings settings(String modelName) {
        var settings = new LinkAiSettings();
        settings.setEnabled(true);
        var commentExtraction = new LinkAiSettings.CommentExtraction();
        commentExtraction.setEnabled(true);
        commentExtraction.setModelName(modelName);
        settings.setCommentExtraction(commentExtraction);
        return settings.normalized();
    }

    private static LinkCommentExtractionResult extractionResult() {
        return new LinkCommentExtractionResult(
            "https://halo.run",
            "Halo",
            null,
            null,
            null
        );
    }

    private static MockServerRequest postRequest(String path, Object body) {
        var httpRequest = MockServerHttpRequest.post(path).build();
        var exchange = MockServerWebExchange.from(httpRequest);
        var builder = MockServerRequest.builder()
            .method(HttpMethod.POST)
            .uri(URI.create(path))
            .exchange(exchange);
        return body == null ? builder.body(Mono.empty()) : builder.body(Mono.just(body));
    }
}
