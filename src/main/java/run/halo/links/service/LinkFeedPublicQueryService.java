package run.halo.links.service;

import reactor.core.publisher.Mono;
import run.halo.links.rss.LinkFeedItemQuery;
import run.halo.links.vo.LinkFeedItemPageVo;

public interface LinkFeedPublicQueryService {

    Mono<Boolean> isPublicEnabled();

    Mono<LinkFeedItemPageVo> listFeeds(String groupName, LinkFeedItemQuery query);
}
