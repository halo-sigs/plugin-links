package run.halo.links.rss;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface LinkFeedItemStore {

    void upsert(LinkFeedItem item);

    int upsertAll(List<LinkFeedItem> items);

    List<LinkFeedItem> listRecent(LinkFeedItemQuery query);

    boolean updateRead(String id, boolean read);

    long markUnreadAsRead(String linkName);

    long countUnread();

    Map<String, Long> countUnreadByLinkName();

    boolean updateFavorite(String id, boolean favorite);

    boolean updateReadLater(String id, boolean readLater);

    long count();

    long countByLinkName(String linkName);

    long countByLinkNameAndFeedUrl(String linkName, String feedUrl);

    void deleteOlderThan(Instant cutoff);

    void deleteExcess(long keepCount);

    void deleteExcessByLinkName(String linkName, long keepCount);

    void deleteByLinkName(String linkName);
}
