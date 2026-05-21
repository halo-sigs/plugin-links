package run.halo.links.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.links.service.LinkPublicQueryService;
import run.halo.links.query.LinkPublicQuery;
import run.halo.links.vo.LinkGroupVo;

/**
 * Public endpoint for link group queries.
 */
@Component
@RequiredArgsConstructor
public class LinkGroupQueryEndpoint implements CustomEndpoint {

    private final LinkPublicQueryService linkPublicQueryService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "api.link.halo.run/v1alpha1/LinkGroup";
        return route()
            .GET("linkgroups", this::listGroups,
                builder -> builder.operationId("queryLinkGroups")
                    .description("List public link groups for themes, sorted by group priority.")
                    .tag(tag)
                    .response(responseBuilder()
                        .implementationArray(LinkGroupVo.class))
            )
            .build();
    }

    private Mono<ServerResponse> listGroups(ServerRequest request) {
        LinkPublicQuery query = new LinkPublicQuery(request.exchange());
        return linkPublicQueryService.listAllGroups(query.toListOptions())
            .flatMap(result -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(result));
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.link.halo.run/v1alpha1");
    }
}
