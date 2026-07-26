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
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.links.dto.LinkCommentExtractionResult;
import run.halo.links.dto.LinkCommentExtractionRequest;
import run.halo.links.service.ai.LinkAiOutputException;
import run.halo.links.service.ai.LinkAiService;

import java.util.Map;

/**
 * Console endpoint for AI-powered comment analysis.
 * This endpoint is registered only when the conditional AI adapter is available.
 * If AI Foundation is missing, this bean is skipped and the status endpoint in
 * {@link LinkAiStatusEndpoint} remains functional.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(AiFoundationAvailableCondition.class)
public class LinkAiExtractEndpoint implements CustomEndpoint {

    private final LinkAiService aiService;
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
                return aiService.extract(content, settings.commentExtractionModelName())
                    .flatMap(result -> ServerResponse.ok().bodyValue(result))
                    .onErrorResume(LinkAiOutputException.class, e ->
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
            }))
            .switchIfEmpty(badRequest("Comment content is required."));
    }

    private static Mono<ServerResponse> badRequest(String message) {
        return ServerResponse.badRequest()
            .bodyValue(Map.of("error", message));
    }
}
