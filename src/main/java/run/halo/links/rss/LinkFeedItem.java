package run.halo.links.rss;

import java.time.Instant;
import lombok.Data;

/**
 * RSS or Atom feed item cached outside Halo Extension storage.
 */
@Data
public class LinkFeedItem {
    private String id;
    private String linkName;
    private String feedUrl;
    private String guid;
    private String url;
    private String title;
    private String summary;
    private String author;
    private Instant publishedAt;
    private Instant updatedAt;
    private Instant fetchedAt;
    private String contentHash;
    private Boolean read;
    private Boolean favorite;
    private Boolean readLater;
}
