package run.halo.links.rss;

import java.time.Instant;
import java.util.List;

public interface LinkFeedItemStore {

    void upsert(LinkFeedItem item);

    int upsertAll(List<LinkFeedItem> items);

    List<LinkFeedItem> listRecent(LinkFeedItemQuery query);

    long count();

    long countByLinkName(String linkName);

    void deleteOlderThan(Instant cutoff);

    void deleteExcess(long keepCount);

    void deleteExcessByLinkName(String linkName, long keepCount);
}
