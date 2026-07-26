package run.halo.links.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;
import run.halo.links.dto.LinkCommentRecognitionRequest;

@ExtendWith(MockitoExtension.class)
class AiFoundationLinkAiServiceTest {

    @Mock
    ExtensionGetter extensionGetter;

    @Mock
    AiModelService aiModelService;

    @Mock
    LanguageModel languageModel;

    AiFoundationLinkAiService service;

    @BeforeEach
    void setUp() {
        service = new AiFoundationLinkAiService(extensionGetter);
    }

    @Test
    void shouldExtractManualFieldsFromMapWithoutRequiringOptionalProperties() {
        givenModel("model-a", Map.of(
            "url", "https://example.com",
            "displayName", "Example"
        ));

        StepVerifier.create(service.extract("申请友链", "model-a"))
            .assertNext(result -> {
                assertThat(result.url()).isEqualTo("https://example.com");
                assertThat(result.displayName()).isEqualTo("Example");
                assertThat(result.logo()).isNull();
            })
            .verifyComplete();

        var request = capturedRequest();
        assertThat(request.getOutput().getSchema()).doesNotContainKey("required");
    }

    @Test
    void shouldRecognizeCommentWithBoundedRetriesAndTimeout() {
        givenModel("model-a", Map.of(
            "isLinkApplication", true,
            "url", "https://example.com",
            "feedUrls", List.of("https://example.com/feed.xml")
        ));
        var input = new LinkCommentRecognitionRequest(
            "请交换友链",
            "POST",
            "About Halo",
            "Alice",
            "https://alice.example"
        );

        StepVerifier.create(service.recognize(input, "model-a"))
            .assertNext(result -> {
                assertThat(result.isLinkApplication()).isTrue();
                assertThat(result.url()).isEqualTo("https://example.com");
                assertThat(result.feedUrls())
                    .containsExactly("https://example.com/feed.xml");
            })
            .verifyComplete();

        var request = capturedRequest();
        assertThat(request.getMaxRetries()).isEqualTo(2);
        assertThat(request.getTimeouts().getTotalTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(request.getOutput().getSchema().get("required"))
            .isEqualTo(List.of("isLinkApplication"));
        assertThat(request.getSystem()).contains("untrusted", "v1");
        assertThat(request.getPrompt())
            .contains("请交换友链", "POST", "About Halo", "Alice",
                "https://alice.example");
        assertThat(request.getPrompt())
            .doesNotContain("ipAddress", "userAgent", "email");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAllowNullRecognitionOptionalFieldsInStructuredOutputSchema() {
        var properties = (Map<String, Map<String, Object>>) AiFoundationLinkAiService
            .recognitionOutput()
            .getSchema()
            .get("properties");

        assertNullableTypes(properties.get("url"), "string");
        assertNullableTypes(properties.get("displayName"), "string");
        assertNullableTypes(properties.get("logo"), "string");
        assertNullableTypes(properties.get("description"), "string");
        assertNullableTypes(properties.get("backlink"), "string");
        assertNullableTypes(properties.get("feedUrls"), "array");
    }

    @Test
    void shouldTreatNullRecognitionOptionalFieldsAsOmitted() {
        var output = new LinkedHashMap<String, Object>();
        output.put("isLinkApplication", true);
        output.put("url", "https://example.com");
        output.put("displayName", "Example");
        output.put("logo", null);
        output.put("description", null);
        output.put("backlink", null);
        output.put("feedUrls", null);
        givenModel("model-a", output);

        StepVerifier.create(service.recognize(new LinkCommentRecognitionRequest(
                "申请友链", "POST", "About", "Example", null), "model-a"))
            .assertNext(result -> {
                assertThat(result.isLinkApplication()).isTrue();
                assertThat(result.url()).isEqualTo("https://example.com");
                assertThat(result.displayName()).isEqualTo("Example");
                assertThat(result.logo()).isNull();
                assertThat(result.description()).isNull();
                assertThat(result.backlink()).isNull();
                assertThat(result.feedUrls()).isEmpty();
            })
            .verifyComplete();
    }

    @Test
    void shouldRejectNonStringRecognitionOptionalField() {
        givenModel("model-a", Map.of(
            "isLinkApplication", true,
            "logo", 42
        ));

        StepVerifier.create(service.recognize(new LinkCommentRecognitionRequest(
                "申请友链", "POST", "About", "Example", null), "model-a"))
            .expectError(IllegalStateException.class)
            .verify();
    }

    @Test
    void shouldRejectNonStringRecognitionFeedUrl() {
        givenModel("model-a", Map.of(
            "isLinkApplication", true,
            "feedUrls", List.of(42)
        ));

        StepVerifier.create(service.recognize(new LinkCommentRecognitionRequest(
                "申请友链", "POST", "About", "Example", null), "model-a"))
            .expectError(IllegalStateException.class)
            .verify();
    }

    @Test
    void shouldRejectMalformedManualExtractionOutput() {
        givenModel("model-a", 42);

        StepVerifier.create(service.extract("comment", "model-a"))
            .expectError(IllegalStateException.class)
            .verify();
    }

    @Test
    void shouldRejectMalformedRecognitionOutput() {
        givenModel("model-a", Map.of("isLinkApplication", "yes"));

        StepVerifier.create(service.recognize(new LinkCommentRecognitionRequest(
                "comment", "LINKS", "链接", null, null), "model-a"))
            .expectError(IllegalStateException.class)
            .verify();
    }

    @Test
    void shouldTerminateRecognitionAtTotalTimeout() {
        when(extensionGetter.getEnabledExtension(AiModelService.class))
            .thenReturn(Mono.just(aiModelService));
        when(aiModelService.languageModel("model-a")).thenReturn(Mono.just(languageModel));
        when(languageModel.generateText(any(GenerateTextRequest.class))).thenReturn(Mono.never());

        StepVerifier.withVirtualTime(() -> service.recognize(
                new LinkCommentRecognitionRequest(
                    "comment", "LINKS", "链接", null, null),
                "model-a"
            ))
            .thenAwait(Duration.ofSeconds(31))
            .expectError(TimeoutException.class)
            .verify();
    }

    @Test
    void shouldReportModelAsNotOperationalWhenNoEnabledServiceExists() {
        when(extensionGetter.getEnabledExtension(AiModelService.class)).thenReturn(Mono.empty());

        StepVerifier.create(service.isOperational("model-a"))
            .expectNext(false)
            .verifyComplete();
    }

    private void givenModel(String modelName, Object output) {
        when(extensionGetter.getEnabledExtension(AiModelService.class))
            .thenReturn(Mono.just(aiModelService));
        when(aiModelService.languageModel(modelName)).thenReturn(Mono.just(languageModel));
        var result = new GenerateTextResult();
        result.setOutput(output);
        when(languageModel.generateText(any(GenerateTextRequest.class)))
            .thenReturn(Mono.just(result));
    }

    private GenerateTextRequest capturedRequest() {
        var captor = ArgumentCaptor.forClass(GenerateTextRequest.class);
        org.mockito.Mockito.verify(languageModel).generateText(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private static void assertNullableTypes(Map<String, Object> schema, String valueType) {
        var alternatives = (List<Map<String, Object>>) schema.get("anyOf");
        assertThat(alternatives)
            .extracting(alternative -> alternative.get("type"))
            .containsExactly(valueType, "null");
    }
}
