package run.halo.links.rss;

import static org.dizitart.no2.collection.Document.createDocument;
import static org.dizitart.no2.filters.Filter.and;
import static org.dizitart.no2.filters.Filter.or;
import static org.dizitart.no2.filters.FluentFilter.where;

import java.time.Instant;
import java.time.format.DateTimeParseException;
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
            if (!collection.hasIndex("feedUrl")) {
                collection.createIndex(IndexOptions.indexOptions(IndexType.NON_UNIQUE), "feedUrl");
            }
            if (!collection.hasIndex("publishedAt")) {
                collection.createIndex(IndexOptions.indexOptions(IndexType.NON_UNIQUE),
                    "publishedAt");
            }
            if (!collection.hasIndex("firstSeenAt")) {
                collection.createIndex(IndexOptions.indexOptions(IndexType.NON_UNIQUE),
                    "firstSeenAt");
            }
            if (!collection.hasIndex("fetchedAt")) {
                collection.createIndex(IndexOptions.indexOptions(IndexType.NON_UNIQUE),
                    "fetchedAt");
            }
            if (!collection.hasIndex("read")) {
                collection.createIndex(IndexOptions.indexOptions(IndexType.NON_UNIQUE), "read");
            }
            if (!collection.hasIndex("favorite")) {
                collection.createIndex(IndexOptions.indexOptions(IndexType.NON_UNIQUE), "favorite");
            }
            if (!collection.hasIndex("readLater")) {
                collection.createIndex(IndexOptions.indexOptions(IndexType.NON_UNIQUE), "readLater");
            }
            Instant migrationTime = Instant.now();
            List<Document> docsToMigrate = new ArrayList<>();
            collection.find().forEach(doc -> {
                if (doc.get("read", Boolean.class) == null
                    || doc.get("favorite", Boolean.class) == null
                    || doc.get("readLater", Boolean.class) == null
                    || !StringUtils.hasText(doc.get("firstSeenAt", String.class))) {
                    docsToMigrate.add(doc);
                }
            });
            docsToMigrate.forEach(doc -> {
                putDefaultState(doc, "read");
                putDefaultState(doc, "favorite");
                putDefaultState(doc, "readLater");
                putDefaultFirstSeenAt(doc, migrationTime);
                collection.update(where("id").eq(doc.get("id", String.class)), doc);
            });
            return !docsToMigrate.isEmpty();
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
            mergeItemState(collection.find(where("id").eq(item.getId())).firstOrNull(), item);
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
                mergeItemState(collection.find(where("id").eq(item.getId())).firstOrNull(), item);
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
        return updateBooleanState(id, "read", read);
    }

    @Override
    public boolean updateFavorite(String id, boolean favorite) {
        return updateBooleanState(id, "favorite", favorite);
    }

    @Override
    public boolean updateReadLater(String id, boolean readLater) {
        return updateBooleanState(id, "readLater", readLater);
    }

    private boolean updateBooleanState(String id, String field, boolean value) {
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("Feed item id must not be blank.");
        }
        boolean updated = database.withCollection(COLLECTION_NAME, collection -> {
            Document doc = collection.find(where("id").eq(id)).firstOrNull();
            if (doc == null) {
                return false;
            }
            doc.put(field, value);
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
    public long countByLinkNameAndFeedUrl(String linkName, String feedUrl) {
        return database.withCollection(COLLECTION_NAME,
            collection -> collection.find(and(where("linkName").eq(linkName),
                where("feedUrl").eq(feedUrl))).size());
    }

    @Override
    public void deleteOlderThan(Instant cutoff) {
        if (cutoff == null) {
            return;
        }
        database.withCollection(COLLECTION_NAME, collection -> {
            collection.remove(and(where("firstSeenAt").lt(toString(cutoff)), unsavedFilter()));
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
            Filter filter = unsavedFilter();
            long deleteCount = Math.min(total - keepCount, collection.find(filter).size());
            if (deleteCount <= 0) {
                return null;
            }
            List<String> ids = new ArrayList<>();
            collection.find(filter, FindOptions.orderBy("publishedAt", SortOrder.Ascending)
                    .limit(deleteCount))
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
            Filter linkFilter = where("linkName").eq(linkName);
            long total = collection.find(linkFilter).size();
            if (total <= keepCount) {
                return null;
            }
            Filter filter = and(linkFilter, unsavedFilter());
            long deleteCount = Math.min(total - keepCount, collection.find(filter).size());
            if (deleteCount <= 0) {
                return null;
            }
            List<String> ids = new ArrayList<>();
            collection.find(filter, FindOptions.orderBy("publishedAt", SortOrder.Ascending)
                    .limit(deleteCount))
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
    public void deleteByLinkName(String linkName) {
        if (!StringUtils.hasText(linkName)) {
            return;
        }
        database.withCollection(COLLECTION_NAME, collection -> {
            collection.remove(where("linkName").eq(linkName));
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
        if (query.getFavorite() != null) {
            Filter favoriteFilter = query.getFavorite()
                ? where("favorite").eq(true)
                : where("favorite").eq(false);
            filter = and(filter, favoriteFilter);
        }
        if (query.getReadLater() != null) {
            Filter readLaterFilter = query.getReadLater()
                ? where("readLater").eq(true)
                : where("readLater").eq(false);
            filter = and(filter, readLaterFilter);
        }
        return filter;
    }

    private static void mergeItemState(Document existing, LinkFeedItem item) {
        if (existing == null) {
            return;
        }
        Instant firstSeenAt = parseInstantOrNull(existing.get("firstSeenAt", String.class));
        if (firstSeenAt == null) {
            firstSeenAt = parseInstantOrNull(existing.get("fetchedAt", String.class));
        }
        if (firstSeenAt != null) {
            item.setFirstSeenAt(firstSeenAt);
        }
        Boolean read = existing.get("read", Boolean.class);
        if (read != null) {
            item.setRead(read);
        }
        Boolean favorite = existing.get("favorite", Boolean.class);
        if (favorite != null) {
            item.setFavorite(favorite);
        }
        Boolean readLater = existing.get("readLater", Boolean.class);
        if (readLater != null) {
            item.setReadLater(readLater);
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
            .put("firstSeenAt", toString(item.getFirstSeenAt()))
            .put("fetchedAt", toString(item.getFetchedAt()))
            .put("contentHash", item.getContentHash())
            .put("read", Boolean.TRUE.equals(item.getRead()))
            .put("favorite", Boolean.TRUE.equals(item.getFavorite()))
            .put("readLater", Boolean.TRUE.equals(item.getReadLater()));
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
            Instant firstSeenAt = parseInstant(doc.get("firstSeenAt", String.class));
            item.setFirstSeenAt(Optional.ofNullable(firstSeenAt).orElse(item.getFetchedAt()));
            item.setContentHash(doc.get("contentHash", String.class));
            item.setRead(Boolean.TRUE.equals(doc.get("read", Boolean.class)));
            item.setFavorite(Boolean.TRUE.equals(doc.get("favorite", Boolean.class)));
            item.setReadLater(Boolean.TRUE.equals(doc.get("readLater", Boolean.class)));
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

    private static Filter unsavedFilter() {
        return and(where("favorite").eq(false), where("readLater").eq(false));
    }

    private static void putDefaultState(Document doc, String field) {
        if (doc.get(field, Boolean.class) == null) {
            doc.put(field, false);
        }
    }

    private static void putDefaultFirstSeenAt(Document doc, Instant fallback) {
        if (StringUtils.hasText(doc.get("firstSeenAt", String.class))) {
            return;
        }
        Instant firstSeenAt = parseInstantOrNull(doc.get("fetchedAt", String.class));
        doc.put("firstSeenAt", toString(Optional.ofNullable(firstSeenAt).orElse(fallback)));
    }

    private static Instant parseInstantOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
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
