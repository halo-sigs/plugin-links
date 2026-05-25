package run.halo.links.rss;

import reactor.core.publisher.Mono;

public interface LinkFeedService {

    Mono<LinkFeedDiscoveryResult> discover(String websiteUrl);

    Mono<LinkFeedRefreshResult> refresh(String linkName);

    LinkFeedItemPage listItems(LinkFeedItemQuery query);
}
