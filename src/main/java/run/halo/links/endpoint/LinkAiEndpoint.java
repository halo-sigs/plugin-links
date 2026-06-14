package run.halo.links.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;
import static run.halo.app.extension.index.query.Queries.equal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.dto.LinkAiFeatureStatus;
import run.halo.links.dto.LinkCommentDTO;

/**
 * Console endpoint for listing recent comments.
 */
@Component
@RequiredArgsConstructor
public class LinkAiEndpoint implements CustomEndpoint {

    private final ReactiveExtensionClient client;
    private final LinkAiSettingsFetcher settingsFetcher;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "console.api.link.halo.run/v1alpha1/LinkAi";
        return route()
            .GET("links/-/ai-status", this::getAiStatus,
                builder -> builder
                    .operationId("getAiStatus")
                    .description("Get runtime status for AI-assisted link features.")
                    .tag(tag)
                    .response(responseBuilder()
                        .implementation(LinkAiFeatureStatus.class))
            )
            .GET("links/-/recent-comments", this::listRecentComments,
                builder -> builder
                    .operationId("listRecentComments")
                    .description("List the 10 most recent approved comments for friend-link extraction.")
                    .tag(tag)
                    .response(responseBuilder()
                        .implementationArray(LinkCommentDTO.class))
            )
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.link.halo.run/v1alpha1");
    }

    Mono<ServerResponse> getAiStatus(ServerRequest request) {
        return settingsFetcher.fetch()
            .map(settings -> new LinkAiFeatureStatus(
                settings.aiEnabled(),
                AiFoundationAvailableCondition.isAiFoundationAvailable(),
                settings.commentExtractionEnabled(),
                settings.commentExtractionModelName()
            ))
            .flatMap(status -> ServerResponse.ok().bodyValue(status));
    }

    Mono<ServerResponse> listRecentComments(ServerRequest request) {
        return settingsFetcher.fetch()
            .flatMap(settings -> {
                if (!settings.commentExtractionEnabled()
                    || !AiFoundationAvailableCondition.isAiFoundationAvailable()) {
                    return ServerResponse.notFound().build();
                }
                return doListRecentComments();
            });
    }

    private Mono<ServerResponse> doListRecentComments() {
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
}
