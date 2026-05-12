package run.halo.links.finders;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.links.extension.Link;
import run.halo.links.vo.LinkGroupVo;
import run.halo.links.vo.LinkVo;
import java.util.List;

/**
 * A finder for {@link run.halo.links.Link}.
 *
 * @author guqing
 * @author ryanwang
 */
public interface LinkFinder {

    Flux<LinkVo> listBy(String group);

    Flux<LinkGroupVo> groupBy();

    Mono<List<LinkVo>> random(Integer maxSize);

    Mono<Integer> count();
}
