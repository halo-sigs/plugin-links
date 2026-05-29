package run.halo.links.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;
import static run.halo.app.extension.index.query.Queries.equal;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
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
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;
import run.halo.links.dto.LinkCommentAnalysisResult;
import run.halo.links.dto.LinkCommentDTO;

/**
 * Console endpoint for AI-powered comment analysis for friend-link extraction.
 */
@Component
@RequiredArgsConstructor
public class LinkAiEndpoint implements CustomEndpoint {

    private final ReactiveExtensionClient client;

    private final ExtensionGetter extensionGetter;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "console.api.link.halo.run/v1alpha1/LinkAi";
        return route()
            .GET("links/-/recent-comments", this::listRecentComments,
                builder -> builder
                    .operationId("listRecentComments")
                    .description("List the 10 most recent approved comments for friend-link extraction.")
                    .tag(tag)
                    .response(responseBuilder()
                        .implementationArray(LinkCommentDTO.class))
            )
            .POST("links/-/ai-extract", this::extractFromComment,
                builder -> builder
                    .operationId("extractLinkFromComment")
                    .description("Extract friend-link information from comment content using AI.")
                    .tag(tag)
                    .requestBody(requestBodyBuilder()
                        .description("Comment content to analyze")
                        .implementation(Map.class))
                    .response(responseBuilder()
                        .implementation(LinkCommentAnalysisResult.class))
            )
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.link.halo.run/v1alpha1");
    }

    Mono<ServerResponse> listRecentComments(ServerRequest request) {
        var listOptions = ListOptions.builder()
            .andQuery(equal("spec.approved", "true"))
            .build();
        var pageRequest = PageRequestImpl.of(1, 10,
            Sort.by(Sort.Direction.DESC, "metadata.creationTimestamp"));

        return client.listBy(Comment.class, listOptions, pageRequest)
            .map(result -> result.getItems().stream()
                .map(this::toCommentDto)
                .toList())
            .flatMap(dtos -> ServerResponse.ok().bodyValue(dtos));
    }

    private LinkCommentDTO toCommentDto(Comment comment) {
        var spec = comment.getSpec();
        var owner = spec.getOwner();
        return new LinkCommentDTO(
            comment.getMetadata().getName(),
            spec.getRaw(),
            spec.getContent(),
            owner != null ? owner.getDisplayName() : null,
            owner != null ? owner.getName() : null,
            spec.getCreationTime()
        );
    }

    Mono<ServerResponse> extractFromComment(ServerRequest request) {
        return request.bodyToMono(Map.class)
            .flatMap(body -> {
                String content = (String) body.get("content");
                if (content == null || content.isBlank()) {
                    return badRequest("Comment content is required.");
                }
                return doExtract(content)
                    .flatMap(result -> ServerResponse.ok().bodyValue(result))
                    .onErrorResume(StructuredOutputValidationException.class, e -> {
                        return ServerResponse.badRequest()
                            .bodyValue(Map.of(
                                "error", "AI failed to parse the comment into structured data. Please try again or fill in manually.",
                                "detail", e.getMessage()
                            ));
                    })
                    .onErrorResume(Exception.class, e ->
                        ServerResponse.status(HttpStatus.BAD_GATEWAY)
                            .bodyValue(Map.of("error", e.getMessage())));
            });
    }

    private Mono<LinkCommentAnalysisResult> doExtract(String content) {
        return extensionGetter.getEnabledExtension(AiModelService.class)
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "AI foundation plugin is not installed or enabled.")))
            .flatMap(AiModelService::defaultLanguageModel)
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No default language model is configured in AI foundation plugin.")))
            .flatMap(model -> {
                String prompt = buildExtractionPrompt(content);
                return model.generateText(GenerateTextRequest.builder()
                    .system("You are a helpful assistant that extracts friend-link (友链) application information from Chinese or English blog comments. Extract the website URL, name, logo URL, and description if present.")
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
            The comment may contain a website URL, site name, logo URL, and description.
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
                toStringValue(map.get("description"))
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
