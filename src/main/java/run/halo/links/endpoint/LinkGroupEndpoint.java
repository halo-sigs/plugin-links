package run.halo.links.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import run.halo.links.extension.LinkGroup;
import run.halo.links.service.LinkGroupService;

@Component
@RequiredArgsConstructor
public class LinkGroupEndpoint implements CustomEndpoint {

    private final LinkGroupService linkGroupService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "console.api.link.halo.run/v1alpha1/LinkGroup";
        return route()
            .DELETE("linkgroups/{name}", this::deleteLinkGroup,
                builder -> builder.operationId("DeleteLinkGroup")
                    .description("Delete link group.")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("name")
                        .in(ParameterIn.PATH)
                        .description("Link group name")
                        .implementation(String.class)
                        .required(true)
                    )
                    .parameter(parameterBuilder()
                        .name("deleteLinks")
                        .in(ParameterIn.QUERY)
                        .description("Delete links in the group; when false, links become ungrouped")
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
}
