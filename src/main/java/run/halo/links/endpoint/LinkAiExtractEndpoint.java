package run.halo.links.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.RequiredArgsConstructor;
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
import run.halo.links.dto.LinkCommentAnalysisResult;
import run.halo.links.dto.LinkCommentExtractRequest;

import java.util.Map;

/**
 * Console endpoint for AI-powered comment analysis.
 * This class imports ai-foundation classes and will only load when the
 * ai-foundation plugin is present. If ai-foundation is missing, this bean
 * is skipped and the recent-comments endpoint in {@link LinkAiEndpoint}
 * remains functional.
 */
@Component
@RequiredArgsConstructor
@Conditional(AiFoundationAvailableCondition.class)
public class LinkAiExtractEndpoint implements CustomEndpoint {

    private final ExtensionGetter extensionGetter;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "console.api.link.halo.run/v1alpha1/LinkAi";
        return route()
            .POST("links/-/ai-extract", this::extractFromComment,
                builder -> builder
                    .operationId("extractLinkFromComment")
                    .description("Extract friend-link information from comment content using AI.")
                    .tag(tag)
                    .requestBody(requestBodyBuilder()
                        .description("Comment content to analyze")
                        .implementation(LinkCommentExtractRequest.class))
                    .response(responseBuilder()
                        .implementation(LinkCommentAnalysisResult.class))
            )
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.link.halo.run/v1alpha1");
    }

    @Operation(
        requestBody = @RequestBody(
            content = @Content(schema = @Schema(implementation = LinkCommentExtractRequest.class))
        )
    )
    Mono<ServerResponse> extractFromComment(ServerRequest request) {
        return request.bodyToMono(LinkCommentExtractRequest.class)
            .flatMap(req -> {
                String content = req.getContent();
                if (content == null || content.isBlank()) {
                    return badRequest("Comment content is required.");
                }
                return doExtract(content)
                    .flatMap(result -> ServerResponse.ok().bodyValue(result))
                    .onErrorResume(StructuredOutputValidationException.class, e ->
                        ServerResponse.badRequest()
                            .bodyValue(Map.of(
                                "error", "AI failed to parse the comment into structured data. Please try again or fill in manually.",
                                "detail", e.getMessage()
                            )))
                    .onErrorResume(Exception.class, e ->
                        ServerResponse.status(HttpStatus.BAD_GATEWAY)
                            .bodyValue(Map.of("error", e.getMessage())));
            });
    }

    private Mono<LinkCommentAnalysisResult> doExtract(String content) {
        return extensionGetter.getEnabledExtension(AiModelService.class)
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "AI foundation plugin is not installed or enabled.")))
            .flatMap(AiModelService::languageModel)
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No default language model is configured in AI foundation plugin.")))
            .flatMap(model -> {
                String prompt = buildExtractionPrompt(content);
                return model.generateText(GenerateTextRequest.builder()
                    .system("You are a helpful assistant that extracts friend-link (友链) application information from Chinese or English blog comments. Extract the website URL, name, logo URL, description, and RSS feed URL if present.")
                    .prompt(prompt)
                    .output(OutputSpec.object(LinkCommentAnalysisResult.class))
                    .maxOutputTokens(500)
                    .build());
            })
            .map(result -> toAnalysisResult(result.getOutput()));
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

    @SuppressWarnings("unchecked")
    private static LinkCommentAnalysisResult toAnalysisResult(Object output) {
        if (output instanceof LinkCommentAnalysisResult result) {
            return result;
        }
        if (output instanceof Map<?, ?> map) {
            return new LinkCommentAnalysisResult(
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
