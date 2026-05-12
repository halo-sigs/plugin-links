package run.halo.links;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.PluginContext;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.links.finders.LinkFinder;
import run.halo.links.vo.LinkGroupVo;

@Component
@RequiredArgsConstructor
public class LinkRouter {

    private final LinkFinder linkFinder;
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

    private Mono<List<LinkGroupVo>> linkGroups() {
        return linkFinder.groupBy()
            .collectList();
    }

    Mono<String> getLinkTitle() {
        return this.settingFetcher.getSettingValue("base")
            .map(setting -> setting.get("title").asText())
            .defaultIfEmpty("链接");
    }
}
