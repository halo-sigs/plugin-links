package run.halo.links.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;
import run.halo.links.dto.LinkAiSettings;
import run.halo.links.dto.LinkCommentAnalysisResult;
import run.halo.links.dto.LinkCommentExtractRequest;

@ExtendWith(MockitoExtension.class)
class LinkAiExtractEndpointTest {

    @Mock
    ExtensionGetter extensionGetter;

    @Mock
    LinkAiSettingsFetcher settingsFetcher;

    @Mock
    AiModelService aiModelService;

    @Mock
    LanguageModel languageModel;

    @Test
    void shouldUseConfiguredLanguageModel() {
        when(settingsFetcher.fetch()).thenReturn(Mono.just(settings("model-a")));
        when(extensionGetter.getEnabledExtension(AiModelService.class))
            .thenReturn(Mono.just(aiModelService));
        when(aiModelService.languageModel("model-a")).thenReturn(Mono.just(languageModel));
        when(languageModel.generateText(any(GenerateTextRequest.class))).thenReturn(Mono.just(
            generateTextResult()));

        var endpoint = new LinkAiExtractEndpoint(extensionGetter, settingsFetcher);
        var body = new LinkCommentExtractRequest();
        body.setContent("站点：https://halo.run");
        var request = postRequest("/links/-/ai-extract", body);

        StepVerifier.create(endpoint.endpoint().route(request)
                .flatMap(handler -> handler.handle(request)))
            .assertNext(response -> assertThat(response.statusCode().value()).isEqualTo(200))
            .verifyComplete();

        verify(aiModelService).languageModel("model-a");
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

    private static GenerateTextResult generateTextResult() {
        var result = new GenerateTextResult();
        result.setOutput(new LinkCommentAnalysisResult(
            "https://halo.run",
            "Halo",
            null,
            null,
            null
        ));
        return result;
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
