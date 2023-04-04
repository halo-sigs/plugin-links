package run.halo.links;

import static java.util.Comparator.comparing;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static run.halo.app.extension.router.selector.SelectorUtil.labelAndFieldSelectorToPredicate;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.util.comparator.Comparators;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.SortResolver;
import run.halo.app.extension.Extension;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.router.IListRequest;
import run.halo.links.finders.LinkFinder;
import run.halo.links.vo.LinkGroupVo;

@Configuration
@RequiredArgsConstructor
public class LinkRouter {

    private final LinkFinder linkFinder;
    private final ReactiveExtensionClient client;
    private final String tag = "api.plugin.halo.run/v1alpha1/Link";

    @Bean
    RouterFunction<ServerResponse> linkTemplateRoute() {
        return route(GET("/links"),
            request -> ServerResponse.ok().render("links",
                Map.of("groups", linkGroups())));
    }

    @Bean
    RouterFunction<ServerResponse> linkRoute() {
        return SpringdocRouteBuilder.route()
            .nest(RequestPredicates.path("/apis/api.plugin.halo.run/v1alpha1/plugins/PluginLinks"),
                this::nested,
                builder -> builder.operationId("PluginLinksEndpoints")
                    .description("Plugin links Endpoints").tag(tag)
            )
            .build();
    }

    RouterFunction<ServerResponse> nested() {
        return SpringdocRouteBuilder.route()
            .GET("/groups/{name}/links", this::listLinkByGroup,
                builder -> builder.operationId("listLinkByGroup")
                    .description("Lists link by group name")
                    .tag(tag)
                    .parameter(parameterBuilder().name("name")
                        .in(ParameterIn.PATH)
                        .required(true)
                        .implementation(String.class)
                    )
            ).build();
    }

    Mono<ServerResponse> listLinkByGroup(ServerRequest request) {
        String name = request.pathVariable("name");
        LinkQuery linkQuery = new LinkQuery(request.exchange());
        return listLink(name, linkQuery)
            .flatMap(links -> ServerResponse.ok().bodyValue(links));
    }

    private Mono<ListResult<Link>> listLink(String groupName, LinkQuery query) {
        return client.list(Link.class,
            link -> Objects.equals(groupName, link.getSpec().getGroupName())
                && query.toPredicate().test(link),
            query.toComparator(),
            query.getPage(),
            query.getSize()
        );
    }

    static class LinkQuery extends IListRequest.QueryListRequest {
        private final ServerWebExchange exchange;

        public LinkQuery(ServerWebExchange exchange) {
            super(exchange.getRequest().getQueryParams());
            this.exchange = exchange;
        }

        @Schema(description = "Keyword to search links under the group")
        public String getKeyword() {
            return queryParams.getFirst("keyword");
        }

        @ArraySchema(uniqueItems = true,
            arraySchema = @Schema(name = "sort",
                description = "Sort property and direction of the list result. Supported fields: "
                    + "creationTimestamp, priority"),
            schema = @Schema(description = "like field,asc or field,desc",
                implementation = String.class,
                example = "creationTimestamp,desc"))
        public Sort getSort() {
            return SortResolver.defaultInstance.resolve(exchange);
        }

        public Predicate<Link> toPredicate() {
            Predicate<Link> keywordPredicate = link -> {
                var keyword = getKeyword();
                if (StringUtils.isBlank(keyword)) {
                    return true;
                }
                var displayName = link.getSpec().getDisplayName();
                String keywordToSearch = keyword.trim().toLowerCase();
                return displayName.toLowerCase().contains(keywordToSearch)
                    || link.getSpec().getDescription().contains(keywordToSearch)
                    || link.getSpec().getUrl().contains(keywordToSearch);
            };
            Predicate<Extension> labelAndFieldSelectorToPredicate =
                labelAndFieldSelectorToPredicate(getLabelSelector(), getFieldSelector());
            return keywordPredicate.and(labelAndFieldSelectorToPredicate);
        }

        public Comparator<Link> toComparator() {
            var sort = getSort();
            var ctOrder = sort.getOrderFor("creationTimestamp");
            var priorityOrder = sort.getOrderFor("priority");
            List<Comparator<Link>> comparators = new ArrayList<>();
            if (ctOrder != null) {
                Comparator<Link> comparator =
                    comparing(link -> link.getMetadata().getCreationTimestamp());
                if (ctOrder.isDescending()) {
                    comparator = comparator.reversed();
                }
                comparators.add(comparator);
            }
            if (priorityOrder != null) {
                Comparator<Link> comparator =
                    comparing(link -> link.getSpec().getPriority(),
                        Comparators.nullsLow());
                if (priorityOrder.isDescending()) {
                    comparator = comparator.reversed();
                }
                comparators.add(comparator);
            }
            comparators.add(compareCreationTimestamp(false));
            comparators.add(compareName(true));
            return comparators.stream()
                .reduce(Comparator::thenComparing)
                .orElse(null);
        }

        public static <E extends Extension> Comparator<E> compareCreationTimestamp(boolean asc) {
            var comparator =
                Comparator.<E, Instant>comparing(e -> e.getMetadata().getCreationTimestamp());
            return asc ? comparator : comparator.reversed();
        }

        public static <E extends Extension> Comparator<E> compareName(boolean asc) {
            var comparator = Comparator.<E, String>comparing(e -> e.getMetadata().getName());
            return asc ? comparator : comparator.reversed();
        }
    }

    private Mono<List<LinkGroupVo>> linkGroups() {
        return linkFinder.groupBy()
            .collectList();
    }
}
