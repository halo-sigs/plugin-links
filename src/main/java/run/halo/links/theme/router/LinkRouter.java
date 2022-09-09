package run.halo.links.theme.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.links.Link;
import run.halo.links.theme.finders.LinkFinder;

@Component
public class LinkRouter {

    private final LinkFinder linkFinder;


    public LinkRouter(LinkFinder linkFinder) {
        this.linkFinder = linkFinder;
    }


    @Bean
    RouterFunction<ServerResponse> linkRoute() {
        return route(GET("/links"),
                request -> ServerResponse.ok().render("links", Map.of("links", links())));
    }

    private Mono<List<Link>> links() {
        return Mono.defer(() -> Mono.just(linkFinder.listAll())).publishOn(Schedulers.boundedElastic());
    }
}
