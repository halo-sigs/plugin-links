package run.halo.links.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import run.halo.links.rss.LinkFeedItem;
import run.halo.links.rss.LinkFeedItemQuery;

class SqliteLinkFeedItemStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldUpsertDuplicateItemsByStableId() {
        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            SqliteLinkFeedItemStore store = new SqliteLinkFeedItemStore(database);
            LinkFeedItem first = item("item-1", "link-a", "Original", "2026-05-20T10:00:00Z");
            LinkFeedItem updated = item("item-1", "link-a", "Updated", "2026-05-20T10:00:00Z");

            store.upsert(first);
            store.upsert(updated);

            LinkFeedItemQuery query = new LinkFeedItemQuery();
            query.setLinkName("link-a");

            assertThat(store.countByLinkName("link-a")).isEqualTo(1);
            assertThat(store.listRecent(query))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getTitle()).isEqualTo("Updated");
                    assertThat(item.getFirstSeenAt())
                        .isEqualTo(Instant.parse("2026-05-22T12:00:00Z"));
                });
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldCountItemsByLinkNameAndFeedUrl() {
        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            SqliteLinkFeedItemStore store = new SqliteLinkFeedItemStore(database);
            store.upsert(item("feed-a-1", "link-a", "First", "2026-05-20T10:00:00Z",
                "https://example.com/feed.xml"));
            store.upsert(item("feed-a-2", "link-a", "Second", "2026-05-21T10:00:00Z",
                "https://example.com/feed.xml"));
            store.upsert(item("feed-b-1", "link-a", "Other Feed", "2026-05-22T10:00:00Z",
                "https://example.com/comments.xml"));

            assertThat(store.countByLinkName("link-a")).isEqualTo(3);
            assertThat(store.countByLinkNameAndFeedUrl("link-a", "https://example.com/feed.xml"))
                .isEqualTo(2);
            assertThat(store.countByLinkNameAndFeedUrl("link-a",
                "https://example.com/comments.xml")).isEqualTo(1);
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldDeleteExcessItemsByLinkName() {
        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            SqliteLinkFeedItemStore store = new SqliteLinkFeedItemStore(database);
            store.upsert(item("old", "link-a", "Old", "2026-05-20T10:00:00Z"));
            store.upsert(item("middle", "link-a", "Middle", "2026-05-21T10:00:00Z"));
            store.upsert(item("new", "link-a", "New", "2026-05-22T10:00:00Z"));

            store.deleteExcessByLinkName("link-a", 2);

            LinkFeedItemQuery query = new LinkFeedItemQuery();
            query.setLinkName("link-a");
            assertThat(store.listRecent(query))
                .extracting(LinkFeedItem::getId)
                .containsExactly("new", "middle");
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldPageItemsWithStableIdTieBreakerForSamePublishedTime() {
        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            SqliteLinkFeedItemStore store = new SqliteLinkFeedItemStore(database);
            String samePublishedAt = "2026-05-20T10:00:00Z";
            store.upsert(item("item-a", "link-a", "A", samePublishedAt));
            store.upsert(item("item-c", "link-a", "C", samePublishedAt));
            store.upsert(item("item-b", "link-a", "B", samePublishedAt));
            store.upsert(item("older", "link-a", "Older", "2026-05-19T10:00:00Z"));

            LinkFeedItemQuery firstPage = new LinkFeedItemQuery();
            firstPage.setLimit(2);
            assertThat(store.listRecent(firstPage))
                .extracting(LinkFeedItem::getId)
                .containsExactly("item-c", "item-b");

            LinkFeedItemQuery secondPage = new LinkFeedItemQuery();
            secondPage.setBeforePublishedAt(Instant.parse(samePublishedAt));
            secondPage.setBeforeId("item-b");
            secondPage.setLimit(2);
            assertThat(store.listRecent(secondPage))
                .extracting(LinkFeedItem::getId)
                .containsExactly("item-a", "older");
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldPageMixedPrecisionTimestampsInChronologicalOrder() {
        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            SqliteLinkFeedItemStore store = new SqliteLinkFeedItemStore(database);
            store.upsert(item("precision-0", "link-a", "0", "2026-05-20T10:00:00Z"));
            store.upsert(item("precision-3", "link-a", "3", "2026-05-20T10:00:00.123Z"));
            store.upsert(item("precision-6", "link-a", "6", "2026-05-20T10:00:00.123456Z"));
            store.upsert(item("precision-9", "link-a", "9",
                "2026-05-20T10:00:00.123456789Z"));

            LinkFeedItemQuery firstPage = new LinkFeedItemQuery();
            firstPage.setLimit(2);
            assertThat(store.listRecent(firstPage))
                .extracting(LinkFeedItem::getId)
                .containsExactly("precision-9", "precision-6");

            LinkFeedItemQuery secondPage = new LinkFeedItemQuery();
            secondPage.setBeforePublishedAt(Instant.parse("2026-05-20T10:00:00.123456Z"));
            secondPage.setBeforeId("precision-6");
            secondPage.setLimit(2);
            assertThat(store.listRecent(secondPage))
                .extracting(LinkFeedItem::getId)
                .containsExactly("precision-3", "precision-0");

            store.deleteExcess(2);
            assertThat(store.listRecent(new LinkFeedItemQuery()))
                .extracting(LinkFeedItem::getId)
                .containsExactly("precision-9", "precision-6");
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldFilterAndPreserveReadState() {
        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            SqliteLinkFeedItemStore store = new SqliteLinkFeedItemStore(database);
            LinkFeedItem item = item("item-1", "link-a", "Unread", "2026-05-20T10:00:00Z");
            store.upsert(item);

            assertThat(store.updateRead("item-1", true)).isTrue();
            assertThat(store.updateRead("missing", true)).isFalse();

            LinkFeedItemQuery readQuery = new LinkFeedItemQuery();
            readQuery.setRead(true);
            assertThat(store.listRecent(readQuery))
                .extracting(LinkFeedItem::getId)
                .containsExactly("item-1");

            LinkFeedItemQuery unreadQuery = new LinkFeedItemQuery();
            unreadQuery.setRead(false);
            assertThat(store.listRecent(unreadQuery)).isEmpty();

            LinkFeedItem refreshed = item("item-1", "link-a", "Updated", "2026-05-20T10:00:00Z");
            store.upsert(refreshed);

            assertThat(store.listRecent(readQuery))
                .singleElement()
                .satisfies(updated -> {
                    assertThat(updated.getTitle()).isEqualTo("Updated");
                    assertThat(updated.getRead()).isTrue();
                });
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldMarkAllUnreadItemsAsRead() {
        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            SqliteLinkFeedItemStore store = new SqliteLinkFeedItemStore(database);
            store.upsert(item("a-unread", "link-a", "Unread A", "2026-05-20T10:00:00Z"));
            store.upsert(item("b-unread", "link-b", "Unread B", "2026-05-21T10:00:00Z"));
            store.upsert(item("already-read", "link-b", "Read", "2026-05-22T10:00:00Z"));
            store.updateRead("already-read", true);

            assertThat(store.markUnreadAsRead(null)).isEqualTo(2);

            LinkFeedItemQuery readQuery = new LinkFeedItemQuery();
            readQuery.setRead(true);
            assertThat(store.listRecent(readQuery))
                .extracting(LinkFeedItem::getId)
                .containsExactly("already-read", "b-unread", "a-unread");

            LinkFeedItemQuery unreadQuery = new LinkFeedItemQuery();
            unreadQuery.setRead(false);
            assertThat(store.listRecent(unreadQuery)).isEmpty();
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldMarkSelectedLinkUnreadItemsAsRead() {
        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            SqliteLinkFeedItemStore store = new SqliteLinkFeedItemStore(database);
            store.upsert(item("a-unread-new", "link-a", "Unread A New",
                "2026-05-22T10:00:00Z"));
            store.upsert(item("a-unread-old", "link-a", "Unread A Old",
                "2026-05-20T10:00:00Z"));
            store.upsert(item("a-read", "link-a", "Read A", "2026-05-21T10:00:00Z"));
            store.upsert(item("b-unread", "link-b", "Unread B", "2026-05-23T10:00:00Z"));
            store.updateRead("a-read", true);

            assertThat(store.markUnreadAsRead("link-a")).isEqualTo(2);

            LinkFeedItemQuery linkAReadQuery = new LinkFeedItemQuery();
            linkAReadQuery.setLinkName("link-a");
            linkAReadQuery.setRead(true);
            assertThat(store.listRecent(linkAReadQuery))
                .extracting(LinkFeedItem::getId)
                .containsExactly("a-unread-new", "a-read", "a-unread-old");

            LinkFeedItemQuery unreadQuery = new LinkFeedItemQuery();
            unreadQuery.setRead(false);
            assertThat(store.listRecent(unreadQuery))
                .extracting(LinkFeedItem::getId)
                .containsExactly("b-unread");
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldReturnZeroWhenMarkingReadWithoutUnreadItems() {
        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            SqliteLinkFeedItemStore store = new SqliteLinkFeedItemStore(database);
            store.upsert(item("already-read", "link-a", "Read", "2026-05-20T10:00:00Z"));
            store.updateRead("already-read", true);

            assertThat(store.markUnreadAsRead(null)).isZero();
            assertThat(store.markUnreadAsRead("missing-link")).isZero();

            LinkFeedItemQuery readQuery = new LinkFeedItemQuery();
            readQuery.setRead(true);
            assertThat(store.listRecent(readQuery))
                .extracting(LinkFeedItem::getId)
                .containsExactly("already-read");
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldCountUnreadItems() {
        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            SqliteLinkFeedItemStore store = new SqliteLinkFeedItemStore(database);
            store.upsert(item("a-unread-1", "link-a", "Unread A 1", "2026-05-20T10:00:00Z"));
            store.upsert(item("a-unread-2", "link-a", "Unread A 2", "2026-05-21T10:00:00Z"));
            store.upsert(item("a-read", "link-a", "Read A", "2026-05-22T10:00:00Z"));
            store.upsert(item("b-unread", "link-b", "Unread B", "2026-05-23T10:00:00Z"));
            store.updateRead("a-read", true);

            assertThat(store.countUnread()).isEqualTo(3);
            assertThat(store.countUnreadByLinkName())
                .containsEntry("link-a", 2L)
                .containsEntry("link-b", 1L)
                .doesNotContainKey("missing-link");

            store.markUnreadAsRead("link-a");

            assertThat(store.countUnread()).isEqualTo(1);
            assertThat(store.countUnreadByLinkName())
                .doesNotContainKey("link-a")
                .containsEntry("link-b", 1L);
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldPreserveFirstSeenAtWhenRefreshingExistingItem() {
        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            SqliteLinkFeedItemStore store = new SqliteLinkFeedItemStore(database);
            LinkFeedItem first = item("item-1", "link-a", "Original", "2026-05-20T10:00:00Z",
                "2026-05-21T12:00:00Z", "2026-05-21T12:00:00Z");
            LinkFeedItem refreshed = item("item-1", "link-a", "Updated", "2026-05-20T10:00:00Z",
                "2026-05-24T12:00:00Z", "2026-05-24T12:00:00Z");

            store.upsert(first);
            store.upsert(refreshed);

            assertThat(store.listRecent(new LinkFeedItemQuery()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getTitle()).isEqualTo("Updated");
                    assertThat(item.getFirstSeenAt())
                        .isEqualTo(Instant.parse("2026-05-21T12:00:00Z"));
                    assertThat(item.getFetchedAt())
                        .isEqualTo(Instant.parse("2026-05-24T12:00:00Z"));
                });
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldFilterToggleAndPreserveSavedStates() {
        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            SqliteLinkFeedItemStore store = new SqliteLinkFeedItemStore(database);
            store.upsert(item("item-1", "link-a", "Saved", "2026-05-20T10:00:00Z"));

            assertThat(store.updateFavorite("item-1", true)).isTrue();
            assertThat(store.updateReadLater("item-1", true)).isTrue();
            assertThat(store.updateFavorite("missing", true)).isFalse();
            assertThat(store.updateReadLater("missing", true)).isFalse();

            LinkFeedItemQuery favoriteQuery = new LinkFeedItemQuery();
            favoriteQuery.setFavorite(true);
            assertThat(store.listRecent(favoriteQuery))
                .extracting(LinkFeedItem::getId)
                .containsExactly("item-1");

            LinkFeedItemQuery readLaterQuery = new LinkFeedItemQuery();
            readLaterQuery.setReadLater(true);
            assertThat(store.listRecent(readLaterQuery))
                .extracting(LinkFeedItem::getId)
                .containsExactly("item-1");

            LinkFeedItemQuery unsavedQuery = new LinkFeedItemQuery();
            unsavedQuery.setFavorite(false);
            assertThat(store.listRecent(unsavedQuery)).isEmpty();

            LinkFeedItem refreshed = item("item-1", "link-a", "Updated", "2026-05-20T10:00:00Z");
            store.upsert(refreshed);

            assertThat(store.listRecent(favoriteQuery))
                .singleElement()
                .satisfies(updated -> {
                    assertThat(updated.getTitle()).isEqualTo("Updated");
                    assertThat(updated.getFavorite()).isTrue();
                    assertThat(updated.getReadLater()).isTrue();
                });
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldKeepSavedItemsWhenDeletingByAge() {
        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            SqliteLinkFeedItemStore store = new SqliteLinkFeedItemStore(database);
            store.upsert(item("old-unsaved", "link-a", "Old", "2026-05-20T10:00:00Z",
                "2026-05-20T12:00:00Z", "2026-05-22T12:00:00Z"));
            store.upsert(item("old-favorite", "link-a", "Favorite", "2026-05-20T11:00:00Z",
                "2026-05-20T13:00:00Z", "2026-05-22T12:00:00Z", true, false));
            store.upsert(item("old-later", "link-a", "Later", "2026-05-20T12:00:00Z",
                "2026-05-20T14:00:00Z", "2026-05-22T12:00:00Z", false, true));
            store.upsert(item("new-unsaved", "link-a", "New", "2026-05-22T10:00:00Z"));

            store.deleteOlderThan(Instant.parse("2026-05-21T00:00:00Z"));

            assertThat(store.listRecent(new LinkFeedItemQuery()))
                .extracting(LinkFeedItem::getId)
                .containsExactly("new-unsaved", "old-later", "old-favorite");
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldKeepRecentlySeenItemsWithOldPublishedAtWhenDeletingByAge() {
        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            SqliteLinkFeedItemStore store = new SqliteLinkFeedItemStore(database);
            store.upsert(item("quiet-feed-item", "link-a", "Quiet", "2024-08-26T02:23:35Z",
                "2026-05-22T12:00:00Z", "2026-05-22T12:00:00Z"));

            store.deleteOlderThan(Instant.parse("2026-05-21T00:00:00Z"));

            assertThat(store.listRecent(new LinkFeedItemQuery()))
                .extracting(LinkFeedItem::getId)
                .containsExactly("quiet-feed-item");
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldKeepSavedItemsWhenDeletingExcessItems() {
        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            SqliteLinkFeedItemStore store = new SqliteLinkFeedItemStore(database);
            store.upsert(item("old-unsaved", "link-a", "Old", "2026-05-20T10:00:00Z"));
            store.upsert(item("middle-unsaved", "link-a", "Middle", "2026-05-21T10:00:00Z"));
            store.upsert(item("new-unsaved", "link-a", "New", "2026-05-22T10:00:00Z"));
            store.upsert(item("old-favorite", "link-a", "Favorite", "2026-05-19T10:00:00Z",
                true, false));

            store.deleteExcess(2);

            assertThat(store.listRecent(new LinkFeedItemQuery()))
                .extracting(LinkFeedItem::getId)
                .containsExactly("new-unsaved", "old-favorite");
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldKeepSavedItemsWhenDeletingExcessItemsByLinkName() {
        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            SqliteLinkFeedItemStore store = new SqliteLinkFeedItemStore(database);
            store.upsert(item("a-old-unsaved", "link-a", "Old", "2026-05-20T10:00:00Z"));
            store.upsert(item("a-new-unsaved", "link-a", "New", "2026-05-22T10:00:00Z"));
            store.upsert(item("a-old-later", "link-a", "Later", "2026-05-19T10:00:00Z",
                false, true));
            store.upsert(item("b-old-unsaved", "link-b", "Other", "2026-05-18T10:00:00Z"));

            store.deleteExcessByLinkName("link-a", 2);

            assertThat(store.listRecent(new LinkFeedItemQuery()))
                .extracting(LinkFeedItem::getId)
                .containsExactly("a-new-unsaved", "a-old-later", "b-old-unsaved");
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldDeleteAllItemsByLinkNameIncludingSavedStates() {
        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            SqliteLinkFeedItemStore store = new SqliteLinkFeedItemStore(database);
            store.upsert(item("a-read", "link-a", "Read", "2026-05-20T10:00:00Z"));
            store.upsert(item("a-favorite", "link-a", "Favorite", "2026-05-21T10:00:00Z",
                true, false));
            store.upsert(item("a-later", "link-a", "Later", "2026-05-22T10:00:00Z",
                false, true));
            store.upsert(item("b-saved", "link-b", "Other", "2026-05-23T10:00:00Z",
                true, true));
            store.updateRead("a-read", true);

            store.deleteByLinkName("link-a");

            assertThat(store.countByLinkName("link-a")).isZero();
            assertThat(store.listRecent(new LinkFeedItemQuery()))
                .extracting(LinkFeedItem::getId)
                .containsExactly("b-saved");
        } finally {
            database.destroy();
        }
    }

    private static LinkFeedItem item(String id, String linkName, String title, String publishedAt) {
        return item(id, linkName, title, publishedAt, false, false);
    }

    private static LinkFeedItem item(String id, String linkName, String title, String publishedAt,
        String feedUrl) {
        LinkFeedItem item = item(id, linkName, title, publishedAt, false, false);
        item.setFeedUrl(feedUrl);
        return item;
    }

    private static LinkFeedItem item(String id, String linkName, String title, String publishedAt,
        boolean favorite, boolean readLater) {
        return item(id, linkName, title, publishedAt, "2026-05-22T12:00:00Z",
            "2026-05-22T12:00:00Z", favorite, readLater);
    }

    private static LinkFeedItem item(String id, String linkName, String title, String publishedAt,
        String firstSeenAt, String fetchedAt) {
        return item(id, linkName, title, publishedAt, firstSeenAt, fetchedAt, false, false);
    }

    private static LinkFeedItem item(String id, String linkName, String title, String publishedAt,
        String firstSeenAt, String fetchedAt, boolean favorite, boolean readLater) {
        LinkFeedItem item = new LinkFeedItem();
        item.setId(id);
        item.setLinkName(linkName);
        item.setFeedUrl("https://example.com/feed.xml");
        item.setGuid(id);
        item.setUrl("https://example.com/" + id);
        item.setTitle(title);
        item.setPublishedAt(Instant.parse(publishedAt));
        item.setFirstSeenAt(Instant.parse(firstSeenAt));
        item.setFetchedAt(Instant.parse(fetchedAt));
        item.setContentHash(id);
        item.setRead(false);
        item.setFavorite(favorite);
        item.setReadLater(readLater);
        return item;
    }
}
