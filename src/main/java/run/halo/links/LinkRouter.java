package run.halo.links;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.links.finders.LinkFinder;
import run.halo.links.vo.LinkGroupVo;

@Configuration
public class LinkRouter {

    private final LinkFinder linkFinder;


    public LinkRouter(LinkFinder linkFinder) {
        this.linkFinder = linkFinder;
    }

    @Bean
    RouterFunction<ServerResponse> linkRoute() {
        return route(GET("/links"),
            request -> ServerResponse.ok().render("links",
                Map.of("groups", linkGroups())));
    }

    private Mono<List<LinkGroupVo>> linkGroups() {
        return Mono.defer(() -> Mono.just(linkFinder.groupBy()))
            .publishOn(Schedulers.boundedElastic());
    }
}
