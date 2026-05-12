package run.halo.links;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.thymeleaf.context.LazyContextVariable;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.plugin.PluginContext;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.links.finders.LinkFinder;
import run.halo.links.finders.LinkPublicQueryService;
import run.halo.links.vo.LinkGroupVo;
import run.halo.links.vo.LinkVo;

@Component
@RequiredArgsConstructor
public class LinkRouter {

    private static final Duration BLOCKING_TIMEOUT = Duration.ofSeconds(10);
    private static final String TEMPLATE_ID = "_templateId";

    private final LinkFinder linkFinder;
    private final LinkPublicQueryService linkPublicQueryService;
    private final PluginContext pluginContext;
    private final ReactiveSettingFetcher settingFetcher;

    @Bean
    RouterFunction<ServerResponse> linkTemplateRoute() {
        return route(GET("/links"), listHandler());
    }

    private HandlerFunction<ServerResponse> listHandler() {
        return request -> {
            String group = queryParam(request, "group");

            var links = new LazyContextVariable<List<LinkVo>>() {
                @Override
                protected List<LinkVo> loadValue() {
                    return loadLinks(group).block(BLOCKING_TIMEOUT);
                }
            };

            var simpleGroups = new LazyContextVariable<List<LinkGroupVo>>() {
                @Override
                protected List<LinkGroupVo> loadValue() {
                    return linkPublicQueryService.listAllGroups(ListOptions.builder().build())
                        .block(BLOCKING_TIMEOUT);
                }
            };

            var groups = new LazyContextVariable<List<LinkGroupVo>>() {
                @Override
                protected List<LinkGroupVo> loadValue() {
                    return linkFinder.groupBy().collectList().block(BLOCKING_TIMEOUT);
                }
            };

            var linksTitle = new LazyContextVariable<String>() {
                @Override
                protected String loadValue() {
                    return getLinkTitle().block(BLOCKING_TIMEOUT);
                }
            };

            Map<String, Object> model = new HashMap<>();
            model.put("links", links);
            model.put("simpleGroups", simpleGroups);
            model.put("groups", groups);
            model.put("group", group);
            model.put("pluginName", pluginContext.getName());
            model.put("linksTitle", linksTitle);
            model.put(TEMPLATE_ID, "links");
            return ServerResponse.ok().render("links", model);
        };
    }

    private Mono<List<LinkVo>> loadLinks(String group) {
        var options = ListOptions.builder();
        options.andQuery(isNull("metadata.deletionTimestamp"));
        if (StringUtils.isNotBlank(group)) {
            options.andQuery(equal("spec.groupName", group));
        }
        return linkPublicQueryService.listLinks(
                options.build(),
                PageRequestImpl.of(1, Integer.MAX_VALUE, defaultLinkSort()))
            .map(ListResult::getItems);
    }

    private static String queryParam(ServerRequest request, String name) {
        return request.queryParam(name)
            .filter(StringUtils::isNotBlank)
            .orElse(null);
    }

    Mono<String> getLinkTitle() {
        return this.settingFetcher.getSettingValue("base")
            .map(setting -> setting.get("title").asText())
            .defaultIfEmpty("链接");
    }

    static Sort defaultLinkSort() {
        return Sort.by(
            Sort.Order.asc("spec.priority"),
            Sort.Order.asc("metadata.creationTimestamp"),
            Sort.Order.asc("metadata.name")
        );
    }
}
