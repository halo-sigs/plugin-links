package run.halo.links.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.links.dto.SortRequest;
import run.halo.links.extension.LinkGroup;
import run.halo.links.service.LinkGroupService;

@Component
@RequiredArgsConstructor
public class LinkGroupEndpoint implements CustomEndpoint {

    private final LinkGroupService linkGroupService;
    private final ReactiveExtensionClient client;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "console.api.link.halo.run/v1alpha1/LinkGroup";
        return route()
            .POST("linkgroups/-/sort", this::sortLinkGroups,
                builder -> builder.operationId("sortLinkGroups")
                    .description("Update link group priorities according to the provided ordered group names.")
                    .tag(tag)
                    .requestBody(requestBodyBuilder()
                        .description("Ordered metadata names of link groups.")
                        .implementation(SortRequest.class))
                    .response(responseBuilder()
                        .responseCode("200"))
            )
            .DELETE("linkgroups/{name}", this::deleteLinkGroup,
                builder -> builder.operationId("DeleteLinkGroup")
                    .description("Delete a link group and optionally delete the links assigned to it.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Metadata name of the link group to delete.")
                        .implementation(String.class)
                        .required(true)
                    )
                    .parameter(parameterBuilder()
                        .name("deleteLinks")
                        .in(ParameterIn.QUERY)
                        .description("Whether to delete links in the group; when false, links become ungrouped.")
                        .required(false)
                        .implementation(Boolean.class)
                    )
                    .response(responseBuilder().implementation(LinkGroup.class))
            )
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("console.api.link.halo.run/v1alpha1");
    }

    private Mono<ServerResponse> deleteLinkGroup(ServerRequest request) {
        String name = request.pathVariable("name");
        boolean deleteLinks = request.queryParam("deleteLinks")
            .map(Boolean::parseBoolean)
            .orElse(false);
        return linkGroupService.deleteLinkGroup(name, deleteLinks)
            .flatMap(group -> ServerResponse.ok().bodyValue(group));
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
