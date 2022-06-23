package run.halo.links;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ApiVersion;

/**
 * @author guqing
 * @since 2.0.0
 */
@RestController
@RequestMapping("/apples")
@ApiVersion("v1alpha1")
public class ApplesController {

    @GetMapping
    public Mono<String> hello() {
        return Mono.just("Hello world");
    }
}
