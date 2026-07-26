package run.halo.links.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
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
import run.halo.links.dto.LinkCommentSummaryDTO;
import run.halo.links.service.ai.LinkAiService;

/**
 * Console endpoint for AI-assisted link feature status and recent comments.
 */
@Component
@RequiredArgsConstructor
public class LinkAiStatusEndpoint implements CustomEndpoint {

    private final ReactiveExtensionClient client;
    private final LinkAiSettingsFetcher settingsFetcher;
    private final LinkApplicationSettingsFetcher applicationSettingsFetcher;
    private final ObjectProvider<LinkAiService> aiServiceProvider;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "console.api.link.halo.run/v1alpha1/LinkAi";
        return route()
            .GET("links/-/ai-status", this::getAiStatus,
                builder -> builder
                    .operationId("getLinkAiFeatureStatus")
                    .description("Get runtime status for AI-assisted link features.")
                    .tag(tag)
                    .response(responseBuilder()
                        .implementation(LinkAiFeatureStatus.class))
            )
            .GET("links/-/recent-comments", this::listRecentComments,
                builder -> builder
                    .operationId("listRecentLinkComments")
                    .description("List the 10 most recent comments for friend-link extraction.")
                    .tag(tag)
                    .response(responseBuilder()
                        .implementationArray(LinkCommentSummaryDTO.class))
            )
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.link.halo.run/v1alpha1");
    }

    Mono<ServerResponse> getAiStatus(ServerRequest request) {
        return Mono.zip(settingsFetcher.fetch(), applicationSettingsFetcher.fetch())
            .flatMap(settingsTuple -> {
                var settings = settingsTuple.getT1();
                var applicationSettings = settingsTuple.getT2();
                var aiService = aiServiceProvider.getIfAvailable();
                var serviceOperational = aiService == null
                    ? Mono.just(false)
                    : aiService.isOperational(null).onErrorReturn(false);
                var extractionOperational = operational(
                    aiService,
                    settings.commentExtractionEnabled(),
                    settings.commentExtractionModelName()
                );
                var recognitionOperational = operational(
                    aiService,
                    applicationSettings.commentRecognitionEnabled(),
                    applicationSettings.commentRecognitionModelName()
                );
                return Mono.zip(serviceOperational, extractionOperational,
                        recognitionOperational)
                    .map(operational -> new LinkAiFeatureStatus(
                        settings.aiEnabled(),
                        AiFoundationAvailability.isAvailable(),
                        operational.getT1(),
                        settings.commentExtractionEnabled(),
                        operational.getT2(),
                        settings.commentExtractionModelName(),
                        applicationSettings.commentRecognitionEnabled(),
                        operational.getT3(),
                        applicationSettings.commentRecognitionModelName()
                    ));
            })
            .flatMap(status -> ServerResponse.ok().bodyValue(status));
    }

    Mono<ServerResponse> listRecentComments(ServerRequest request) {
        return settingsFetcher.fetch()
            .flatMap(settings -> {
                var aiService = aiServiceProvider.getIfAvailable();
                if (!settings.commentExtractionEnabled() || aiService == null) {
                    return ServerResponse.notFound().build();
                }
                return aiService.isOperational(settings.commentExtractionModelName())
                    .flatMap(operational -> operational
                        ? doListRecentComments()
                        : ServerResponse.notFound().build());
            });
    }

    private static Mono<Boolean> operational(LinkAiService aiService, boolean enabled,
        String modelName) {
        if (!enabled || aiService == null) {
            return Mono.just(false);
        }
        return aiService.isOperational(modelName)
            .onErrorReturn(false);
    }

    private Mono<ServerResponse> doListRecentComments() {
        var listOptions = ListOptions.builder().build();
        var pageRequest = PageRequestImpl.of(1, 10,
            Sort.by(Sort.Direction.DESC, "metadata.creationTimestamp"));

        return client.listBy(Comment.class, listOptions, pageRequest)
            .map(result -> result.getItems().stream()
                .map(this::toCommentDto)
                .toList())
            .flatMap(dtos -> ServerResponse.ok().bodyValue(dtos));
    }

    private LinkCommentSummaryDTO toCommentDto(Comment comment) {
        var spec = comment.getSpec();
        var owner = spec.getOwner();
        return new LinkCommentSummaryDTO(
            comment.getMetadata().getName(),
            spec.getRaw(),
            spec.getContent(),
            owner != null ? owner.getDisplayName() : null,
            owner != null ? owner.getName() : null,
            spec.getCreationTime()
        );
    }
}
