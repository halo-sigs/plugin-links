package run.halo.links.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerErrorException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.utils.PathUtils;
import run.halo.links.dto.LinkDetailDTO;
import run.halo.links.dto.SortRequest;
import run.halo.links.extension.Link;
import run.halo.links.extension.LinkGroup;
import run.halo.links.dto.LinkRequest;
import run.halo.links.query.LinkQuery;

/**
 * Console endpoint for {@link Link} management.
 */
@Component
@RequiredArgsConstructor
public class LinkEndpoint implements CustomEndpoint {

    private final ReactiveExtensionClient client;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "console.api.link.halo.run/v1alpha1/Link";
        return route()
            .GET("links", this::listLinkByGroup,
                builder -> {
                    builder.operationId("listLinks")
                        .description("Lists link by query parameters")
                        .tag(tag)
                        .response(responseBuilder()
                            .implementation(ListResult.generateGenericClass(Link.class)));
                    LinkQuery.buildParameters(builder);
                }
            )
            .GET("links/-/detail", this::getLinkDetail, builder -> {
                builder.operationId("GetLinkDetail")
                    .description("Get link detail by url")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("url")
                        .description("Link url")
                        .in(ParameterIn.QUERY)
                        .implementation(String.class)
                        .required(true)
                    )
                    .response(responseBuilder().implementation(LinkDetailDTO.class));
            })
            .POST("links/-/sort", this::sortLinks,
                builder -> {
                    builder.operationId("sortLinks")
                        .description("Sort links by priority")
                        .tag(tag)
                        .requestBody(requestBodyBuilder()
                            .implementation(SortRequest.class))
                        .response(responseBuilder()
                            .responseCode("200"));
                }
            )
            .POST("link-groups/-/sort", this::sortLinkGroups,
                builder -> {
                    builder.operationId("sortLinkGroups")
                        .description("Sort link groups by priority")
                        .tag(tag)
                        .requestBody(requestBodyBuilder()
                            .implementation(SortRequest.class))
                        .response(responseBuilder()
                            .responseCode("200"));
                }
            )
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.link.halo.run/v1alpha1");
    }

    Mono<ServerResponse> listLinkByGroup(ServerRequest request) {
        LinkQuery linkQuery = new LinkQuery(request.exchange());
        return listLink(linkQuery)
            .flatMap(links -> ServerResponse.ok().bodyValue(links));
    }

    private Mono<ListResult<Link>> listLink(LinkQuery query) {
        return client.listBy(Link.class, query.toListOptions(), query.toPageRequest());
    }

    private Mono<ServerResponse> getLinkDetail(ServerRequest request) {
        return Mono.fromSupplier(() -> request.queryParam("url")
                .filter(PathUtils::isAbsoluteUri)
                .orElseThrow(() -> new IllegalArgumentException("Invalid url.")))
            .flatMap(url -> Mono.fromSupplier(() -> LinkRequest.getLinkDetail(url))
                .subscribeOn(Schedulers.boundedElastic())
                .publishOn(Schedulers.parallel()))
            .flatMap(dto -> ServerResponse.ok().bodyValue(dto))
            .onErrorResume(IllegalArgumentException.class,
                e -> badRequest(e.getMessage()))
            .onErrorResume(ServerErrorException.class, e -> {
                var msg = e.getMessage();
                if (msg != null
                    && (msg.contains("Invalid URL") || msg.contains("blocked"))) {
                    return badRequest(msg);
                }
                return Mono.error(e);
            });
    }

    private static Mono<ServerResponse> badRequest(String message) {
        return ServerResponse.badRequest()
            .bodyValue(Map.of("error", message));
    }

    Mono<ServerResponse> sortLinks(ServerRequest request) {
        return request.bodyToMono(SortRequest.class)
            .flatMap(sortRequest -> {
                var names = sortRequest.getNames();
                if (names == null || names.isEmpty()) {
                    return ServerResponse.ok().build();
                }
                return Flux.fromIterable(names)
                    .zipWith(Flux.range(0, Integer.MAX_VALUE))
                    .concatMap(tuple -> {
                        String name = tuple.getT1();
                        int priority = tuple.getT2();
                        return client.fetch(Link.class, name)
                            .flatMap(link -> {
                                link.getSpec().setPriority(priority);
                                return client.update(link);
                            });
                    })
                    .then(ServerResponse.ok().build());
            });
    }

    Mono<ServerResponse> sortLinkGroups(ServerRequest request) {
        return request.bodyToMono(SortRequest.class)
            .flatMap(sortRequest -> {
                var names = sortRequest.getNames();
                if (names == null || names.isEmpty()) {
                    return ServerResponse.ok().build();
                }
                return Flux.fromIterable(names)
                    .zipWith(Flux.range(0, Integer.MAX_VALUE))
                    .concatMap(tuple -> {
                        String name = tuple.getT1();
                        int priority = tuple.getT2();
                        return client.fetch(LinkGroup.class, name)
                            .flatMap(group -> {
                                group.getSpec().setPriority(priority);
                                return client.update(group);
                            });
                    })
                    .then(ServerResponse.ok().build());
            });
    }
}
