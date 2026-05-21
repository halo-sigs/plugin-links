package run.halo.links.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListResult;
import run.halo.links.service.LinkPublicQueryService;
import run.halo.links.query.LinkPublicQuery;
import run.halo.links.vo.LinkVo;

/**
 * Public endpoint for link queries.
 */
@Component
@RequiredArgsConstructor
public class LinkQueryEndpoint implements CustomEndpoint {

    private final LinkPublicQueryService linkPublicQueryService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "api.link.halo.run/v1alpha1/Link";
        return route()
            .GET("links", this::listLinks,
                builder -> {
                    builder.operationId("queryLinks")
                        .description("List public links for themes, with optional keyword search and group filtering.")
                        .tag(tag)
                        .response(responseBuilder()
                            .implementation(ListResult.generateGenericClass(LinkVo.class)));
                    LinkPublicQuery.buildParameters(builder);
                }
            )
            .GET("links/-/random", this::linkRandom,
                builder -> {
                    builder.operationId("queryRandomLink")
                        .description("Return a random selection of public links.")
                        .tag(tag)
                        .parameter(parameterBuilder()
                            .name("maxSize")
                            .description("Maximum number of random links to return. Must be between 1 and 100.")
                            .in(ParameterIn.QUERY)
                            .implementation(Integer.class)
                            .required(true)
                        )
                        .response(responseBuilder()
                            .implementationArray(LinkVo.class));
                }
            )
            .GET("links/-/count", this::linkCount,
                builder -> {
                    builder.operationId("queryLinkCount")
                        .description("Return the total number of non-deleted links.")
                        .tag(tag)
                        .response(responseBuilder()
                            .implementation(Integer.class));
                }
            )
            .build();
    }

    Mono<ServerResponse> linkRandom(ServerRequest request) {
        Integer maxSize = request.queryParam("maxSize")
            .map(Integer::parseInt)
            .orElse(1);
        return linkPublicQueryService.random(maxSize)
            .flatMap(links -> ServerResponse.ok().bodyValue(links));
    }

    Mono<ServerResponse> linkCount(ServerRequest request) {
        return linkPublicQueryService.count()
            .flatMap(count -> ServerResponse.ok().bodyValue(count));
    }

    private Mono<ServerResponse> listLinks(ServerRequest request) {
        LinkPublicQuery query = new LinkPublicQuery(request.exchange());
        return linkPublicQueryService.listLinks(query.toListOptions(), query.toPageRequest())
            .flatMap(result -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(result));
    }


    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.link.halo.run/v1alpha1");
    }
}
