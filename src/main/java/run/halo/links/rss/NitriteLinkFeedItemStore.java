package run.halo.links.rss;

import static org.dizitart.no2.collection.Document.createDocument;
import static org.dizitart.no2.filters.Filter.and;
import static org.dizitart.no2.filters.Filter.or;
import static org.dizitart.no2.filters.FluentFilter.where;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.FindOptions;
import org.dizitart.no2.collection.UpdateOptions;
import org.dizitart.no2.common.SortOrder;
import org.dizitart.no2.filters.Filter;
import org.dizitart.no2.index.IndexOptions;
import org.dizitart.no2.index.IndexType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import run.halo.links.nitrite.LinksNitriteDatabase;

@Slf4j
@Component
public class NitriteLinkFeedItemStore implements LinkFeedItemStore {

    private static final String COLLECTION_NAME = "link-feed-items";

    private final LinksNitriteDatabase database;

    public NitriteLinkFeedItemStore(LinksNitriteDatabase database) {
        this.database = database;
        boolean migrated = database.withCollection(COLLECTION_NAME, collection -> {
            if (!collection.hasIndex("id")) {
                collection.createIndex(IndexOptions.indexOptions(IndexType.UNIQUE), "id");
            }
            if (!collection.hasIndex("linkName")) {
                collection.createIndex(IndexOptions.indexOptions(IndexType.NON_UNIQUE), "linkName");
            }
            if (!collection.hasIndex("publishedAt")) {
                collection.createIndex(IndexOptions.indexOptions(IndexType.NON_UNIQUE),
                    "publishedAt");
            }
            if (!collection.hasIndex("fetchedAt")) {
                collection.createIndex(IndexOptions.indexOptions(IndexType.NON_UNIQUE),
                    "fetchedAt");
            }
            if (!collection.hasIndex("read")) {
                collection.createIndex(IndexOptions.indexOptions(IndexType.NON_UNIQUE), "read");
            }
            List<Document> docsWithoutReadState = new ArrayList<>();
            collection.find().forEach(doc -> {
                if (doc.get("read", Boolean.class) == null) {
                    docsWithoutReadState.add(doc);
                }
            });
            docsWithoutReadState.forEach(doc -> {
                doc.put("read", false);
                collection.update(where("id").eq(doc.get("id", String.class)), doc);
            });
            return !docsWithoutReadState.isEmpty();
        });
        if (migrated) {
            database.commit();
        }
    }

    @Override
    public void upsert(LinkFeedItem item) {
        if (!StringUtils.hasText(item.getId())) {
            throw new IllegalArgumentException("Feed item id must not be blank.");
        }
        database.withCollection(COLLECTION_NAME, collection -> {
            mergeReadState(collection.find(where("id").eq(item.getId())).firstOrNull(), item);
            collection.update(where("id").eq(item.getId()), toDocument(item),
                UpdateOptions.updateOptions(true));
            return null;
        });
        database.commit();
    }

    @Override
    public int upsertAll(List<LinkFeedItem> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        database.withCollection(COLLECTION_NAME, collection -> {
            for (LinkFeedItem item : items) {
                if (!StringUtils.hasText(item.getId())) {
                    throw new IllegalArgumentException("Feed item id must not be blank.");
                }
                mergeReadState(collection.find(where("id").eq(item.getId())).firstOrNull(), item);
                collection.update(where("id").eq(item.getId()), toDocument(item),
                    UpdateOptions.updateOptions(true));
            }
            return null;
        });
        database.commit();
        return items.size();
    }

    @Override
    public List<LinkFeedItem> listRecent(LinkFeedItemQuery query) {
        LinkFeedItemQuery normalized = Optional.ofNullable(query).orElse(new LinkFeedItemQuery());
        int limit = normalized.normalizedLimit();
        Filter filter = buildFilter(normalized);
        return database.withCollection(COLLECTION_NAME, collection -> {
            List<LinkFeedItem> result = new ArrayList<>();
            collection.find(filter, FindOptions.orderBy("publishedAt", SortOrder.Descending)
                    .limit(limit + 1L))
                .forEach(doc -> parseDocument(doc).ifPresent(result::add));
            result.sort(recentComparator());
            if (result.size() > limit) {
                return new ArrayList<>(result.subList(0, limit));
            }
            return result;
        });
    }

    @Override
    public long count() {
        return database.withCollection(COLLECTION_NAME, collection -> collection.size());
    }

    @Override
    public boolean updateRead(String id, boolean read) {
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("Feed item id must not be blank.");
        }
        boolean updated = database.withCollection(COLLECTION_NAME, collection -> {
            Document doc = collection.find(where("id").eq(id)).firstOrNull();
            if (doc == null) {
                return false;
            }
            doc.put("read", read);
            collection.update(where("id").eq(id), doc);
            return true;
        });
        if (updated) {
            database.commit();
        }
        return updated;
    }

    @Override
    public long countByLinkName(String linkName) {
        return database.withCollection(COLLECTION_NAME,
            collection -> collection.find(where("linkName").eq(linkName)).size());
    }

    @Override
    public void deleteOlderThan(Instant cutoff) {
        if (cutoff == null) {
            return;
        }
        database.withCollection(COLLECTION_NAME, collection -> {
            collection.remove(where("publishedAt").lt(toString(cutoff)));
            return null;
        });
        database.commit();
    }

    @Override
    public void deleteExcess(long keepCount) {
        if (keepCount < 0) {
            return;
        }
        database.withCollection(COLLECTION_NAME, collection -> {
            long total = collection.size();
            if (total <= keepCount) {
                return null;
            }
            List<String> ids = new ArrayList<>();
            collection.find(FindOptions.orderBy("publishedAt", SortOrder.Ascending)
                    .limit(total - keepCount))
                .forEach(doc -> {
                    String id = doc.get("id", String.class);
                    if (StringUtils.hasText(id)) {
                        ids.add(id);
                    }
                });
            ids.forEach(id -> collection.remove(where("id").eq(id)));
            return null;
        });
        database.commit();
    }

    @Override
    public void deleteExcessByLinkName(String linkName, long keepCount) {
        if (!StringUtils.hasText(linkName) || keepCount < 0) {
            return;
        }
        database.withCollection(COLLECTION_NAME, collection -> {
            Filter filter = where("linkName").eq(linkName);
            long total = collection.find(filter).size();
            if (total <= keepCount) {
                return null;
            }
            List<String> ids = new ArrayList<>();
            collection.find(filter, FindOptions.orderBy("publishedAt", SortOrder.Ascending)
                    .limit(total - keepCount))
                .forEach(doc -> {
                    String id = doc.get("id", String.class);
                    if (StringUtils.hasText(id)) {
                        ids.add(id);
                    }
                });
            ids.forEach(id -> collection.remove(where("id").eq(id)));
            return null;
        });
        database.commit();
    }

    private Filter buildFilter(LinkFeedItemQuery query) {
        Filter filter = Filter.ALL;
        if (StringUtils.hasText(query.getLinkName())) {
            filter = and(filter, where("linkName").eq(query.getLinkName()));
        }
        if (query.getBeforePublishedAt() != null) {
            String beforePublishedAt = toString(query.getBeforePublishedAt());
            Filter cursor = where("publishedAt").lt(beforePublishedAt);
            if (StringUtils.hasText(query.getBeforeId())) {
                cursor = or(cursor,
                    where("publishedAt").eq(beforePublishedAt)
                        .and(where("id").lt(query.getBeforeId())));
            }
            filter = and(filter, cursor);
        }
        if (query.getRead() != null) {
            Filter readFilter = query.getRead()
                ? where("read").eq(true)
                : where("read").eq(false);
            filter = and(filter, readFilter);
        }
        return filter;
    }

    private static void mergeReadState(Document existing, LinkFeedItem item) {
        if (existing == null) {
            return;
        }
        Boolean read = existing.get("read", Boolean.class);
        if (read != null) {
            item.setRead(read);
        }
    }

    private static Document toDocument(LinkFeedItem item) {
        return createDocument("id", item.getId())
            .put("linkName", item.getLinkName())
            .put("feedUrl", item.getFeedUrl())
            .put("guid", item.getGuid())
            .put("url", item.getUrl())
            .put("title", item.getTitle())
            .put("summary", item.getSummary())
            .put("author", item.getAuthor())
            .put("publishedAt", toString(item.getPublishedAt()))
            .put("updatedAt", toString(item.getUpdatedAt()))
            .put("fetchedAt", toString(item.getFetchedAt()))
            .put("contentHash", item.getContentHash())
            .put("read", Boolean.TRUE.equals(item.getRead()));
    }

    private static Optional<LinkFeedItem> parseDocument(Document doc) {
        if (doc == null) {
            return Optional.empty();
        }
        try {
            LinkFeedItem item = new LinkFeedItem();
            item.setId(doc.get("id", String.class));
            item.setLinkName(doc.get("linkName", String.class));
            item.setFeedUrl(doc.get("feedUrl", String.class));
            item.setGuid(doc.get("guid", String.class));
            item.setUrl(doc.get("url", String.class));
            item.setTitle(doc.get("title", String.class));
            item.setSummary(doc.get("summary", String.class));
            item.setAuthor(doc.get("author", String.class));
            item.setPublishedAt(parseInstant(doc.get("publishedAt", String.class)));
            item.setUpdatedAt(parseInstant(doc.get("updatedAt", String.class)));
            item.setFetchedAt(parseInstant(doc.get("fetchedAt", String.class)));
            item.setContentHash(doc.get("contentHash", String.class));
            item.setRead(Boolean.TRUE.equals(doc.get("read", Boolean.class)));
            return Optional.of(item);
        } catch (Exception e) {
            log.warn("Failed to parse cached feed item: {}", doc, e);
            return Optional.empty();
        }
    }

    private static Comparator<LinkFeedItem> recentComparator() {
        return Comparator.comparing(NitriteLinkFeedItemStore::sortInstant,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .reversed()
            .thenComparing(LinkFeedItem::getId, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private static Instant sortInstant(LinkFeedItem item) {
        if (item.getPublishedAt() != null) {
            return item.getPublishedAt();
        }
        if (item.getUpdatedAt() != null) {
            return item.getUpdatedAt();
        }
        return item.getFetchedAt();
    }

    private static Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Instant.parse(value);
    }

    private static String toString(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
