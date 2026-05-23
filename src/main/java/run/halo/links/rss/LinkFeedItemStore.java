package run.halo.links.rss;

import java.time.Instant;
import java.util.List;

public interface LinkFeedItemStore {

    void upsert(LinkFeedItem item);

    int upsertAll(List<LinkFeedItem> items);

    List<LinkFeedItem> listRecent(LinkFeedItemQuery query);

    boolean updateRead(String id, boolean read);

    boolean updateFavorite(String id, boolean favorite);

    boolean updateReadLater(String id, boolean readLater);

    long count();

    long countByLinkName(String linkName);

    void deleteOlderThan(Instant cutoff);

    void deleteExcess(long keepCount);

    void deleteExcessByLinkName(String linkName, long keepCount);
}
