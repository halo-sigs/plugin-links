package run.halo.links.finders;

import reactor.core.publisher.Flux;
import run.halo.links.vo.LinkGroupVo;
import run.halo.links.vo.LinkVo;

/**
 * A finder for {@link run.halo.links.Link}.
 *
 * @author guqing
 * @author ryanwang
 */
public interface LinkFinder {

    Flux<LinkVo> listBy(String group);

    Flux<LinkGroupVo> groupBy();
}
