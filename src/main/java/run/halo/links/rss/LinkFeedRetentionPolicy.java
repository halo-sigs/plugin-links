package run.halo.links.rss;

import java.time.Duration;

public record LinkFeedRetentionPolicy(long maxItemsTotal, long maxItemsPerLink,
                                      Duration retentionAge) {

    public static LinkFeedRetentionPolicy defaults() {
        return new LinkFeedRetentionPolicy(100_000, 500, Duration.ofDays(180));
    }
}
