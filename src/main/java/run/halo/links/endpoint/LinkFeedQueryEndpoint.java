package run.halo.links.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.links.rss.LinkFeedItemQuery;
import run.halo.links.service.LinkFeedPublicQueryService;
import run.halo.links.vo.LinkFeedItemPageVo;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Public endpoint for link feed queries.
 */
@Component
@RequiredArgsConstructor
public class LinkFeedQueryEndpoint implements CustomEndpoint {

    private final LinkFeedPublicQueryService linkFeedPublicQueryService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "api.link.halo.run/v1alpha1/LinkFeed";
        return route()
            .GET("linkfeeds", this::listFeedItems,
                builder -> builder.operationId("queryLinkFeedItems")
                    .description("List public RSS or Atom feed items with cursor pagination.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("linkName")
                        .description("Filter items by link metadata name.")
                        .in(ParameterIn.QUERY)
                        .implementation(String.class)
                        .required(false)
                    )
                    .parameter(parameterBuilder()
                        .name("groupName")
                        .description("Filter items by current link group metadata name.")
                        .in(ParameterIn.QUERY)
                        .implementation(String.class)
                        .required(false)
                    )
                    .parameter(parameterBuilder()
                        .name("beforePublishedAt")
                        .description("Cursor published time boundary.")
                        .in(ParameterIn.QUERY)
                        .implementation(String.class)
                        .required(false)
                    )
                    .parameter(parameterBuilder()
                        .name("beforeId")
                        .description("Cursor stable item id boundary.")
                        .in(ParameterIn.QUERY)
                        .implementation(String.class)
                        .required(false)
                    )
                    .parameter(parameterBuilder()
                        .name("limit")
                        .description("Maximum number of items to return.")
                        .in(ParameterIn.QUERY)
                        .implementation(Integer.class)
                        .required(false)
                    )
                    .response(responseBuilder().implementation(LinkFeedItemPageVo.class))
            )
            .build();
    }


    private Mono<ServerResponse> listFeedItems(ServerRequest request) {
        String groupName = request.queryParam("groupName")
            .filter(StringUtils::hasText)
            .orElse(null);
        try {
            LinkFeedItemQuery query = parseQuery(request);
            if (StringUtils.hasText(groupName) && StringUtils.hasText(query.getLinkName())) {
                return badRequest("linkName and groupName cannot be used together.");
            }

            return Mono.defer(() -> linkFeedPublicQueryService.listFeeds(groupName, query))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(result -> ServerResponse.ok().bodyValue(result))
                .onErrorResume(IllegalArgumentException.class,
                    error -> badRequest(error.getMessage()));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    private static LinkFeedItemQuery parseQuery(ServerRequest request) {
        LinkFeedItemQuery query = new LinkFeedItemQuery();
        request.queryParam("linkName")
            .filter(StringUtils::hasText)
            .ifPresent(query::setLinkName);
        request.queryParam("beforeId")
            .filter(StringUtils::hasText)
            .ifPresent(query::setBeforeId);
        request.queryParam("beforePublishedAt")
            .filter(StringUtils::hasText)
            .ifPresent(value -> {
                try {
                    query.setBeforePublishedAt(Instant.parse(value));
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("Invalid beforePublishedAt.", e);
                }
            });
        request.queryParam("limit")
            .filter(StringUtils::hasText)
            .ifPresent(value -> {
                try {
                    query.setLimit(Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid limit.", e);
                }
            });
        return query;
    }

    private static Mono<ServerResponse> badRequest(String message) {
        return ServerResponse.badRequest()
            .bodyValue(Map.of("error", message));
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.link.halo.run/v1alpha1");
    }
}
