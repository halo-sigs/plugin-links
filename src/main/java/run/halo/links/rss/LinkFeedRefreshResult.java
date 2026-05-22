package run.halo.links.rss;

import java.time.Instant;
import lombok.Data;

@Data
public class LinkFeedRefreshResult {
    private String linkName;
    private String feedUrl;
    private Instant fetchedAt;
    private Instant latestPublishedAt;
    private long itemCount;
    private int fetchedItemCount;
    private boolean notModified;
    private String etag;
    private String lastModified;
}
