package run.halo.links.rss;

import java.time.Instant;
import java.util.List;
import lombok.Data;

@Data
public class LinkFeedRefreshResult {
    private String linkName;
    private Instant fetchedAt;
    private Instant latestPublishedAt;
    private long itemCount;
    private int fetchedItemCount;
    private boolean partialFailure;
    private List<FeedResult> feeds;

    @Data
    public static class FeedResult {
        private String url;
        private Instant fetchedAt;
        private Instant latestPublishedAt;
        private long itemCount;
        private int fetchedItemCount;
        private boolean notModified;
        private String etag;
        private String lastModified;
        private String error;
    }
}
