package run.halo.links.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.exception.StructuredOutputValidationException;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;
import run.halo.links.dto.LinkCommentExtractionResult;
import run.halo.links.dto.LinkCommentExtractionRequest;

import java.util.Map;

/**
 * Console endpoint for AI-powered comment analysis.
 * This class imports ai-foundation classes and will only load when the
 * ai-foundation plugin is present. If ai-foundation is missing, this bean
 * is skipped and the recent-comments endpoint in {@link LinkAiStatusEndpoint}
 * remains functional.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(AiFoundationAvailableCondition.class)
public class LinkAiExtractEndpoint implements CustomEndpoint {

    private final ExtensionGetter extensionGetter;
    private final LinkAiSettingsFetcher settingsFetcher;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "console.api.link.halo.run/v1alpha1/LinkAi";
        return route()
            .POST("links/-/extract-from-comment", this::extractLinkFromComment,
                builder -> builder
                    .operationId("extractLinkFromComment")
                    .description("Extract friend-link information from comment content using AI.")
                    .tag(tag)
                    .requestBody(requestBodyBuilder()
                        .description("Comment content to analyze")
                        .implementation(LinkCommentExtractionRequest.class))
                    .response(responseBuilder()
                        .implementation(LinkCommentExtractionResult.class))
            )
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.link.halo.run/v1alpha1");
    }

    Mono<ServerResponse> extractLinkFromComment(ServerRequest request) {
        return request.bodyToMono(LinkCommentExtractionRequest.class)
            .flatMap(req -> settingsFetcher.fetch().flatMap(settings -> {
                if (!settings.commentExtractionEnabled()) {
                    return ServerResponse.notFound().build();
                }
                String content = req.getContent();
                if (content == null || content.isBlank()) {
                    return badRequest("Comment content is required.");
                }
                return doExtract(content, settings.commentExtractionModelName())
                    .flatMap(result -> ServerResponse.ok().bodyValue(result))
                    .onErrorResume(StructuredOutputValidationException.class, e ->
                        ServerResponse.badRequest()
                            .bodyValue(Map.of(
                                "error", "AI failed to parse the comment into structured data. Please try again or fill in manually.",
                                "detail", e.getMessage()
                            )))
                    .onErrorResume(Exception.class, e -> {
                        log.warn("[plugin-links] Failed to extract friend-link information from comment", e);
                        return ServerResponse.status(HttpStatus.BAD_GATEWAY)
                            .bodyValue(Map.of("error", "AI service is unavailable. Please try again later."));
                    });
            }));
    }

    private Mono<LinkCommentExtractionResult> doExtract(String content, String modelName) {
        return extensionGetter.getEnabledExtension(AiModelService.class)
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "AI foundation plugin is not installed or enabled.")))
            .flatMap(service -> service.languageModel(modelName))
            .switchIfEmpty(Mono.error(new IllegalStateException(
                modelName == null
                    ? "No default language model is configured in AI foundation plugin."
                    : "Selected language model is not configured in AI foundation plugin.")))
            .flatMap(model -> {
                String prompt = buildExtractionPrompt(content);
                return model.generateText(GenerateTextRequest.builder()
                    .system("You are a helpful assistant that extracts friend-link (友链) application information from Chinese or English blog comments. Extract the website URL, name, logo URL, description, and RSS feed URL if present.")
                    .prompt(prompt)
                    .output(OutputSpec.object(LinkCommentExtractionResult.class))
                    .maxOutputTokens(500)
                    .build());
            })
            .map(result -> toExtractionResult(result.getOutput()));
    }

    private String buildExtractionPrompt(String content) {
        return """
            Extract friend-link application information from the following comment.
            The comment may contain a website URL, site name, logo URL, description, and RSS feed URL.
            Return the extracted fields in the specified format.

            Comment content:
            %s
            """.formatted(content);
    }

    /**
     * Converts the AI model output into {@link LinkCommentExtractionResult}.
     * The structured output spec usually returns the target type directly, but some
     * model implementations may return the result as a generic map; handle both.
     */
    @SuppressWarnings("unchecked")
    private static LinkCommentExtractionResult toExtractionResult(Object output) {
        if (output instanceof LinkCommentExtractionResult result) {
            return result;
        }
        if (output instanceof Map<?, ?> map) {
            return new LinkCommentExtractionResult(
                toStringValue(map.get("url")),
                toStringValue(map.get("displayName")),
                toStringValue(map.get("logo")),
                toStringValue(map.get("description")),
                toStringValue(map.get("rssUrl"))
            );
        }
        throw new IllegalStateException("Unexpected output type: " + output.getClass());
    }

    private static String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static Mono<ServerResponse> badRequest(String message) {
        return ServerResponse.badRequest()
            .bodyValue(Map.of("error", message));
    }
}
