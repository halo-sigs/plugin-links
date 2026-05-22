package run.halo.links.rss;

import java.time.Instant;
import lombok.Data;

@Data
public class LinkFeedItemQuery {
    public static final int DEFAULT_LIMIT = 30;
    public static final int MAX_LIMIT = 100;

    private String linkName;
    private Instant beforePublishedAt;
    private String beforeId;
    private Boolean read;
    private int limit = DEFAULT_LIMIT;

    public int normalizedLimit() {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
