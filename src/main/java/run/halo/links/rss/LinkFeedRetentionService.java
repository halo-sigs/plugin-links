package run.halo.links.rss;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkFeedRetentionService {

    private final LinkFeedItemStore itemStore;

    public void enforce(LinkFeedRetentionPolicy policy) {
        LinkFeedRetentionPolicy effective = policy == null
            ? LinkFeedRetentionPolicy.defaults()
            : policy;
        if (effective.retentionAge() != null && !effective.retentionAge().isNegative()
            && !effective.retentionAge().isZero()) {
            itemStore.deleteOlderThan(Instant.now().minus(effective.retentionAge()));
        }
        if (effective.maxItemsTotal() >= 0) {
            itemStore.deleteExcess(effective.maxItemsTotal());
        }
    }

    public void enforceForLink(String linkName, LinkFeedRetentionPolicy policy) {
        LinkFeedRetentionPolicy effective = policy == null
            ? LinkFeedRetentionPolicy.defaults()
            : policy;
        if (effective.maxItemsPerLink() >= 0) {
            itemStore.deleteExcessByLinkName(linkName, effective.maxItemsPerLink());
        }
        enforce(effective);
    }
}
