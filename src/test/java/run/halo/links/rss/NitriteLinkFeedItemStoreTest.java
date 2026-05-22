package run.halo.links.rss;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static LinkFeedItem item(String id, String linkName, String title, String publishedAt) {
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
        return item;
    }
}
