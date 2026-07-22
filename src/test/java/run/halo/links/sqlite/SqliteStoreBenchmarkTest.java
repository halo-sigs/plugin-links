package run.halo.links.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import run.halo.links.rss.LinkFeedItem;
import run.halo.links.rss.LinkFeedItemQuery;

class SqliteStoreBenchmarkTest {

    private static final int ITEM_COUNT = 100_000;
    private static final Duration GENEROUS_LIMIT = Duration.ofMinutes(2);

    @TempDir
    Path tempDir;

    @Test
    void shouldKeepCoreOperationsPracticalAtMaximumCacheSize() throws Exception {
        Path dbPath = tempDir.resolve("links.sqlite");
        LinksSqliteDatabase database = new LinksSqliteDatabase(dbPath);
        SqliteLinkFeedItemStore store = new SqliteLinkFeedItemStore(database);
        List<LinkFeedItem> items = items();

        long writeMillis = elapsedMillis(() -> store.upsertAll(items));
        long listMillis = elapsedMillis(() -> store.listRecent(new LinkFeedItemQuery()));
        long unreadMillis = elapsedMillis(() -> {
            store.countUnread();
            store.countUnreadByLinkName();
        });
        long snapshotMillis = elapsedMillis(database::createSnapshot);
        long cleanupMillis = elapsedMillis(() -> store.deleteExcess(50_000));
        long sizeBytes = Files.size(dbPath);
        database.destroy();

        long reopenMillis = elapsedMillis(() -> {
            LinksSqliteDatabase reopened = new LinksSqliteDatabase(dbPath);
            try {
                assertThat(new SqliteLinkFeedItemStore(reopened).count()).isEqualTo(50_000);
            } finally {
                reopened.destroy();
            }
        });

        System.out.printf("SQLite 100k benchmark: write=%dms list=%dms unread=%dms "
                + "snapshot=%dms cleanup=%dms reopen=%dms size=%d bytes%n",
            writeMillis, listMillis, unreadMillis, snapshotMillis, cleanupMillis,
            reopenMillis, sizeBytes);

        assertThat(writeMillis).isLessThan(GENEROUS_LIMIT.toMillis());
        assertThat(listMillis).isLessThan(GENEROUS_LIMIT.toMillis());
        assertThat(unreadMillis).isLessThan(GENEROUS_LIMIT.toMillis());
        assertThat(snapshotMillis).isLessThan(GENEROUS_LIMIT.toMillis());
        assertThat(cleanupMillis).isLessThan(GENEROUS_LIMIT.toMillis());
        assertThat(reopenMillis).isLessThan(GENEROUS_LIMIT.toMillis());
    }

    private static List<LinkFeedItem> items() {
        List<LinkFeedItem> items = new ArrayList<>(ITEM_COUNT);
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < ITEM_COUNT; i++) {
            LinkFeedItem item = new LinkFeedItem();
            item.setId("item-" + i);
            item.setLinkName("link-" + (i % 100));
            item.setFeedUrl("https://example.com/feed-" + (i % 3) + ".xml");
            item.setGuid("guid-" + i);
            item.setUrl("https://example.com/posts/" + i);
            item.setTitle("Feed item " + i);
            item.setSummary("Summary " + i);
            item.setAuthor("Author " + (i % 10));
            item.setPublishedAt(base.plusSeconds(i));
            item.setUpdatedAt(base.plusSeconds(i + 1L));
            item.setFirstSeenAt(base.plusSeconds(i + 2L));
            item.setFetchedAt(base.plusSeconds(i + 3L));
            item.setContentHash("hash-" + i);
            item.setRead(i % 2 == 0);
            item.setFavorite(false);
            item.setReadLater(false);
            items.add(item);
        }
        return items;
    }

    private static long elapsedMillis(ThrowingRunnable action) throws Exception {
        long start = System.nanoTime();
        action.run();
        return Duration.ofNanos(System.nanoTime() - start).toMillis();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
