package run.halo.links.rss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.dizitart.no2.collection.Document.createDocument;

import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import run.halo.links.nitrite.LinksNitriteDatabase;

class NitriteLinkFeedItemStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldUpsertDuplicateItemsByStableId() {
        LinksNitriteDatabase database = new LinksNitriteDatabase(tempDir.resolve("links-feed.nitrite"));
        try {
            NitriteLinkFeedItemStore store = new NitriteLinkFeedItemStore(database);
            LinkFeedItem first = item("item-1", "link-a", "Original", "2026-05-20T10:00:00Z");
            LinkFeedItem updated = item("item-1", "link-a", "Updated", "2026-05-20T10:00:00Z");

            store.upsert(first);
            store.upsert(updated);

            LinkFeedItemQuery query = new LinkFeedItemQuery();
            query.setLinkName("link-a");

            assertThat(store.countByLinkName("link-a")).isEqualTo(1);
            assertThat(store.listRecent(query))
                .singleElement()
                .extracting(LinkFeedItem::getTitle)
                .isEqualTo("Updated");
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldDeleteExcessItemsByLinkName() {
        LinksNitriteDatabase database = new LinksNitriteDatabase(tempDir.resolve("links-feed.nitrite"));
        try {
            NitriteLinkFeedItemStore store = new NitriteLinkFeedItemStore(database);
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
    void shouldFilterAndPreserveReadState() {
        LinksNitriteDatabase database = new LinksNitriteDatabase(tempDir.resolve("links-feed.nitrite"));
        try {
            NitriteLinkFeedItemStore store = new NitriteLinkFeedItemStore(database);
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
    void shouldBackfillMissingSavedStates() {
        LinksNitriteDatabase database = new LinksNitriteDatabase(tempDir.resolve("links-feed.nitrite"));
        try {
            database.withCollection("link-feed-items", collection -> {
                collection.insert(createDocument("id", "legacy")
                    .put("linkName", "link-a")
                    .put("feedUrl", "https://example.com/feed.xml")
                    .put("guid", "legacy")
                    .put("url", "https://example.com/legacy")
                    .put("title", "Legacy")
                    .put("publishedAt", "2026-05-20T10:00:00Z")
                    .put("fetchedAt", "2026-05-22T12:00:00Z")
                    .put("contentHash", "legacy"));
                return null;
            });
            database.commit();

            NitriteLinkFeedItemStore store = new NitriteLinkFeedItemStore(database);

            assertThat(store.listRecent(new LinkFeedItemQuery()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getRead()).isFalse();
                    assertThat(item.getFavorite()).isFalse();
                    assertThat(item.getReadLater()).isFalse();
                });
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldFilterToggleAndPreserveSavedStates() {
        LinksNitriteDatabase database = new LinksNitriteDatabase(tempDir.resolve("links-feed.nitrite"));
        try {
            NitriteLinkFeedItemStore store = new NitriteLinkFeedItemStore(database);
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
        LinksNitriteDatabase database = new LinksNitriteDatabase(tempDir.resolve("links-feed.nitrite"));
        try {
            NitriteLinkFeedItemStore store = new NitriteLinkFeedItemStore(database);
            store.upsert(item("old-unsaved", "link-a", "Old", "2026-05-20T10:00:00Z"));
            store.upsert(item("old-favorite", "link-a", "Favorite", "2026-05-20T11:00:00Z",
                true, false));
            store.upsert(item("old-later", "link-a", "Later", "2026-05-20T12:00:00Z",
                false, true));
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
    void shouldKeepSavedItemsWhenDeletingExcessItems() {
        LinksNitriteDatabase database = new LinksNitriteDatabase(tempDir.resolve("links-feed.nitrite"));
        try {
            NitriteLinkFeedItemStore store = new NitriteLinkFeedItemStore(database);
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
        LinksNitriteDatabase database = new LinksNitriteDatabase(tempDir.resolve("links-feed.nitrite"));
        try {
            NitriteLinkFeedItemStore store = new NitriteLinkFeedItemStore(database);
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

    private static LinkFeedItem item(String id, String linkName, String title, String publishedAt) {
        return item(id, linkName, title, publishedAt, false, false);
    }

    private static LinkFeedItem item(String id, String linkName, String title, String publishedAt,
        boolean favorite, boolean readLater) {
        LinkFeedItem item = new LinkFeedItem();
        item.setId(id);
        item.setLinkName(linkName);
        item.setFeedUrl("https://example.com/feed.xml");
        item.setGuid(id);
        item.setUrl("https://example.com/" + id);
        item.setTitle(title);
        item.setPublishedAt(Instant.parse(publishedAt));
        item.setFetchedAt(Instant.parse("2026-05-22T12:00:00Z"));
        item.setContentHash(id);
        item.setRead(false);
        item.setFavorite(favorite);
        item.setReadLater(readLater);
        return item;
    }
}
