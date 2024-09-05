package run.halo.links;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springframework.data.domain.Sort.Order.asc;
import static org.springframework.data.domain.Sort.Order.desc;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static run.halo.app.extension.index.query.QueryFactory.and;
import static run.halo.app.extension.index.query.QueryFactory.contains;
import static run.halo.app.extension.index.query.QueryFactory.equal;
import static run.halo.app.extension.index.query.QueryFactory.or;
import static run.halo.app.extension.router.selector.SelectorUtil.labelAndFieldSelectorToListOptions;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.fn.builders.operation.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.router.SortableRequest;
import run.halo.app.extension.router.selector.FieldSelector;
import run.halo.app.infra.utils.PathUtils;
import run.halo.app.plugin.PluginContext;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.links.finders.LinkFinder;
import run.halo.links.vo.LinkGroupVo;

@Component
@RequiredArgsConstructor
public class LinkRouter {

    private final LinkFinder linkFinder;
    private final ReactiveExtensionClient client;
    private final String tag = "api.plugin.halo.run/v1alpha1/Link";
    private final PluginContext pluginContext;
    private final ReactiveSettingFetcher settingFetcher;

    @Bean
    RouterFunction<ServerResponse> linkTemplateRoute() {
        return route(GET("/links"),
            request -> ServerResponse.ok().render("links",
                Map.of("groups", linkGroups(),
                    "pluginName", pluginContext.getName(),
                    "linksTitle", getLinkTitle())));
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
            .GET("link-detail", this::getLinkDetail, builder -> {
                builder.operationId("GetLinkDetail")
                    .description("Get link detail by id")
                    .tag(tag)
                    .parameter(parameterBuilder()
                        .name("url")
                        .description("Link url")
                        .in(ParameterIn.QUERY)
                        .implementation(String.class)
                        .required(true)
                    )
                    .response(responseBuilder().implementation(LinkDetailDTO.class));
            }).build();
    }

    private Mono<ServerResponse> getLinkDetail(ServerRequest request) {
        final var url = request.queryParam("url")
            .filter(PathUtils::isAbsoluteUri)
            .orElseThrow(() -> new ServerWebInputException("Invalid url."));
        return Mono.fromSupplier(() -> LinkRequest.getLinkDetail(url))
            .subscribeOn(Schedulers.boundedElastic())
            .publishOn(Schedulers.parallel())
            .flatMap(dto -> ServerResponse.ok().bodyValue(dto));
    }

    Mono<ServerResponse> listLinkByGroup(ServerRequest request) {
        LinkQuery linkQuery = new LinkQuery(request.exchange());
        return listLink(linkQuery)
            .flatMap(links -> ServerResponse.ok().bodyValue(links));
    }

    private Mono<ListResult<Link>> listLink(LinkQuery query) {
        return client.listBy(Link.class, query.toListOptions(), query.toPageRequest());
    }

    static class LinkQuery extends SortableRequest {

        public LinkQuery(ServerWebExchange exchange) {
            super(exchange);
        }

        @Schema(description = "Keyword to search links under the group")
        public String getKeyword() {
            return queryParams.getFirst("keyword");
        }

        @Schema(description = "Link group name")
        public String getGroupName() {
            return queryParams.getFirst("groupName");
        }

        @Override
        public ListOptions toListOptions() {
            var listOptions =
                labelAndFieldSelectorToListOptions(getLabelSelector(), getFieldSelector());
            var query = listOptions.getFieldSelector().query();
            if (StringUtils.isNotBlank(getKeyword())) {
                query = and(query, or(
                    contains("spec.displayName", getKeyword()),
                    contains("spec.description", getKeyword())
                ));
            }

            if (StringUtils.isNotBlank(getGroupName())) {
                query = and(query, equal("spec.groupName", getGroupName()));
            }
            listOptions.setFieldSelector(FieldSelector.of(query));
            return listOptions;
        }

        @Override
        public Sort getSort() {
            return super.getSort()
                .and(Sort.by(desc("metadata.creationTimestamp"),
                    asc("metadata.name"))
                );
        }

        public static void buildParameters(Builder builder) {
            builder.parameter(parameterBuilder()
                    .name("keyword")
                    .description("Keyword to search links under the group")
                    .in(ParameterIn.QUERY)
                    .implementation(String.class)
                    .required(false)
                )
                .parameter(parameterBuilder()
                    .name("groupName")
                    .description("Link group name")
                    .in(ParameterIn.QUERY)
                    .implementation(String.class)
                    .required(false)
                );
            SortableRequest.buildParameters(builder);
        }
    }

    private Mono<List<LinkGroupVo>> linkGroups() {
        return linkFinder.groupBy()
            .collectList();
    }

    Mono<String> getLinkTitle() {
        return this.settingFetcher.get("base")
            .map(setting -> setting.get("title").asText())
            .defaultIfEmpty("链接");
    }
}
