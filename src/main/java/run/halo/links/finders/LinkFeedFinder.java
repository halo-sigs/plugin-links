package run.halo.links.finders;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.links.vo.LinkFeedGroupVo;
import run.halo.links.vo.LinkFeedItemPageVo;
import java.util.Map;

public interface LinkFeedFinder {

    Mono<LinkFeedItemPageVo> list(Map<String, Object> params);

    Flux<LinkFeedGroupVo> groupBy(Integer limit);
}
