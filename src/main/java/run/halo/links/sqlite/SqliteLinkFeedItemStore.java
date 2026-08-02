package run.halo.links.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import run.halo.links.rss.LinkFeedHiddenStateResult;
import run.halo.links.rss.LinkFeedItem;
import run.halo.links.rss.LinkFeedItemQuery;
import run.halo.links.rss.LinkFeedItemStore;

@Slf4j
@Component
public class SqliteLinkFeedItemStore implements LinkFeedItemStore {

    private static final String TABLE = "link_feed_items";
    private static final DateTimeFormatter SQLITE_INSTANT_FORMATTER =
        new DateTimeFormatterBuilder().appendInstant(9).toFormatter();

    private final LinksSqliteDatabase database;

    public SqliteLinkFeedItemStore(LinksSqliteDatabase database) {
        this.database = database;
    }

    @Override
    public void upsert(LinkFeedItem item) {
        validateItem(item);
        database.inTransaction(connection -> {
            upsert(connection, item);
            return null;
        });
    }

    @Override
    public int upsertAll(List<LinkFeedItem> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        items.forEach(SqliteLinkFeedItemStore::validateItem);
        return database.inTransaction(connection -> upsertAll(connection, items));
    }

    @Override
    public List<LinkFeedItem> listRecent(LinkFeedItemQuery query) {
        LinkFeedItemQuery normalized = Optional.ofNullable(query).orElse(new LinkFeedItemQuery());
        int limit = normalized.normalizedFetchLimit();
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(TABLE)
            .append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(normalized.getLinkName())) {
            sql.append(" AND link_name = ?");
            params.add(normalized.getLinkName());
        }
        if (normalized.getBeforePublishedAt() != null) {
            String beforePublishedAt = toString(normalized.getBeforePublishedAt());
            if (StringUtils.hasText(normalized.getBeforeId())) {
                sql.append(" AND (published_at < ? OR (published_at = ? AND id < ?))");
                params.add(beforePublishedAt);
                params.add(beforePublishedAt);
                params.add(normalized.getBeforeId());
            } else {
                sql.append(" AND published_at < ?");
                params.add(beforePublishedAt);
            }
        }
        appendBooleanFilter(sql, params, "read", normalized.getRead());
        appendBooleanFilter(sql, params, "favorite", normalized.getFavorite());
        appendBooleanFilter(sql, params, "read_later", normalized.getReadLater());
        appendBooleanFilter(sql, params, "hidden", Boolean.TRUE.equals(normalized.getHidden()));
        sql.append(" ORDER BY published_at DESC, id DESC LIMIT ?");
        params.add(limit);

        return database.execute(connection -> {
            List<LinkFeedItem> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                bindParams(statement, params);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        parseRow(rows).ifPresent(result::add);
                    }
                }
            }
            result.sort(recentComparator());
            return result;
        });
    }

    @Override
    public boolean updateRead(String id, boolean read) {
        return updateBooleanState(id, "read", read);
    }

    @Override
    public long markUnreadAsRead(String linkName) {
        String sql = "UPDATE " + TABLE + " SET read = 1 WHERE read = 0 AND hidden = 0"
            + (StringUtils.hasText(linkName) ? " AND link_name = ?" : "");
        return database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                if (StringUtils.hasText(linkName)) {
                    statement.setString(1, linkName);
                }
                return (long) statement.executeUpdate();
            }
        });
    }

    @Override
    public long countUnread() {
        return queryForLong("SELECT count(*) FROM " + TABLE + " WHERE read = 0 AND hidden = 0");
    }

    @Override
    public Map<String, Long> countUnreadByLinkName() {
        return database.execute(connection -> {
            Map<String, Long> counts = new LinkedHashMap<>();
            try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("""
                    SELECT link_name, count(*)
                    FROM link_feed_items
                    WHERE read = 0 AND hidden = 0
                      AND link_name IS NOT NULL AND link_name != ''
                    GROUP BY link_name
                    """)) {
                while (rows.next()) {
                    counts.put(rows.getString(1), rows.getLong(2));
                }
            }
            return counts;
        });
    }

    @Override
    public boolean updateFavorite(String id, boolean favorite) {
        return updateBooleanState(id, "favorite", favorite);
    }

    @Override
    public boolean updateReadLater(String id, boolean readLater) {
        return updateBooleanState(id, "read_later", readLater);
    }

    @Override
    public LinkFeedHiddenStateResult updateHidden(List<String> ids, boolean hidden) {
        LinkedHashSet<String> distinctIds = validateHiddenIds(ids);
        return database.inTransaction(connection -> {
            long updatedCount = 0;
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + TABLE + " SET hidden = ? WHERE id = ? AND hidden != ?")) {
                int state = hidden ? 1 : 0;
                for (String id : distinctIds) {
                    statement.setInt(1, state);
                    statement.setString(2, id);
                    statement.setInt(3, state);
                    updatedCount += statement.executeUpdate();
                }
            }
            return new LinkFeedHiddenStateResult(distinctIds.size(), updatedCount);
        });
    }

    @Override
    public long countHidden() {
        return queryForLong("SELECT count(*) FROM " + TABLE + " WHERE hidden = 1");
    }

    @Override
    public long count() {
        return queryForLong("SELECT count(*) FROM " + TABLE);
    }

    @Override
    public long countByLinkName(String linkName) {
        return queryForLong("SELECT count(*) FROM " + TABLE + " WHERE link_name = ?", linkName);
    }

    @Override
    public long countByLinkNameAndFeedUrl(String linkName, String feedUrl) {
        return queryForLong("SELECT count(*) FROM " + TABLE
            + " WHERE link_name = ? AND feed_url = ?", linkName, feedUrl);
    }

    @Override
    public void deleteOlderThan(Instant cutoff) {
        if (cutoff == null) {
            return;
        }
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM link_feed_items
                WHERE first_seen_at < ? AND favorite = 0 AND read_later = 0 AND hidden = 0
                """)) {
                statement.setString(1, toString(cutoff));
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void deleteExcess(long keepCount) {
        if (keepCount < 0) {
            return;
        }
        database.inTransaction(connection -> {
            long total = countWhere(connection, null);
            if (total <= keepCount) {
                return null;
            }
            long deletable = countWhere(connection,
                "favorite = 0 AND read_later = 0 AND hidden = 0");
            deleteOldestUnsaved(connection, null, Math.min(total - keepCount, deletable));
            return null;
        });
    }

    @Override
    public void deleteExcessByLinkName(String linkName, long keepCount) {
        if (!StringUtils.hasText(linkName) || keepCount < 0) {
            return;
        }
        database.inTransaction(connection -> {
            long total = countWhere(connection, "link_name = ?", linkName);
            if (total <= keepCount) {
                return null;
            }
            long deletable = countWhere(connection,
                "link_name = ? AND favorite = 0 AND read_later = 0 AND hidden = 0", linkName);
            deleteOldestUnsaved(connection, linkName,
                Math.min(total - keepCount, deletable));
            return null;
        });
    }

    @Override
    public void deleteByLinkName(String linkName) {
        if (!StringUtils.hasText(linkName)) {
            return;
        }
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + TABLE + " WHERE link_name = ?")) {
                statement.setString(1, linkName);
                statement.executeUpdate();
            }
            return null;
        });
    }

    static int upsertAll(Connection connection, List<LinkFeedItem> items) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(upsertSql())) {
            for (LinkFeedItem item : items) {
                validateItem(item);
                bindUpsert(statement, item);
                statement.addBatch();
            }
            statement.executeBatch();
            return items.size();
        }
    }

    private boolean updateBooleanState(String id, String column, boolean value) {
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("Feed item id must not be blank.");
        }
        return database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + TABLE + " SET " + column + " = ? WHERE id = ?")) {
                statement.setInt(1, value ? 1 : 0);
                statement.setString(2, id);
                return statement.executeUpdate() > 0;
            }
        });
    }

    private static void upsert(Connection connection, LinkFeedItem item) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(upsertSql())) {
            bindUpsert(statement, item);
            statement.executeUpdate();
        }
    }

    private static void deleteOldestUnsaved(Connection connection, String linkName,
        long deleteCount) throws SQLException {
        if (deleteCount <= 0) {
            return;
        }
        String sql = """
            DELETE FROM link_feed_items
            WHERE id IN (
              SELECT id FROM link_feed_items
              WHERE favorite = 0 AND read_later = 0 AND hidden = 0
            """ + (StringUtils.hasText(linkName) ? " AND link_name = ?" : "") + """
              ORDER BY published_at ASC, id ASC
              LIMIT ?
            )
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (StringUtils.hasText(linkName)) {
                statement.setString(index++, linkName);
            }
            statement.setLong(index, deleteCount);
            statement.executeUpdate();
        }
    }

    private static long countWhere(Connection connection, String where, Object... params)
        throws SQLException {
        String sql = "SELECT count(*) FROM " + TABLE
            + (where == null ? "" : " WHERE " + where);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParams(statement, List.of(params));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0;
            }
        }
    }

    private long queryForLong(String sql, Object... params) {
        return database.execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindParams(statement, List.of(params));
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getLong(1) : 0;
                }
            }
        });
    }

    private static String upsertSql() {
        return """
            INSERT INTO link_feed_items (
              id, link_name, feed_url, guid, url, title, summary, author,
              published_at, updated_at, first_seen_at, fetched_at, content_hash,
              read, favorite, read_later, hidden
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
              link_name = excluded.link_name,
              feed_url = excluded.feed_url,
              guid = excluded.guid,
              url = excluded.url,
              title = excluded.title,
              summary = excluded.summary,
              author = excluded.author,
              published_at = excluded.published_at,
              updated_at = excluded.updated_at,
              first_seen_at = COALESCE(
                link_feed_items.first_seen_at,
                link_feed_items.fetched_at,
                excluded.first_seen_at
              ),
              fetched_at = excluded.fetched_at,
              content_hash = excluded.content_hash
            """;
    }

    private static void bindUpsert(PreparedStatement statement, LinkFeedItem item)
        throws SQLException {
        statement.setString(1, item.getId());
        statement.setString(2, item.getLinkName());
        statement.setString(3, item.getFeedUrl());
        statement.setString(4, item.getGuid());
        statement.setString(5, item.getUrl());
        statement.setString(6, item.getTitle());
        statement.setString(7, item.getSummary());
        statement.setString(8, item.getAuthor());
        statement.setString(9, toString(item.getPublishedAt()));
        statement.setString(10, toString(item.getUpdatedAt()));
        statement.setString(11, toString(item.getFirstSeenAt()));
        statement.setString(12, toString(item.getFetchedAt()));
        statement.setString(13, item.getContentHash());
        statement.setInt(14, Boolean.TRUE.equals(item.getRead()) ? 1 : 0);
        statement.setInt(15, Boolean.TRUE.equals(item.getFavorite()) ? 1 : 0);
        statement.setInt(16, Boolean.TRUE.equals(item.getReadLater()) ? 1 : 0);
        statement.setInt(17, Boolean.TRUE.equals(item.getHidden()) ? 1 : 0);
    }

    private static void bindParams(PreparedStatement statement, List<Object> params)
        throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object param = params.get(i);
            if (param instanceof Long value) {
                statement.setLong(i + 1, value);
            } else if (param instanceof Integer value) {
                statement.setInt(i + 1, value);
            } else {
                statement.setString(i + 1, param == null ? null : param.toString());
            }
        }
    }

    static Optional<LinkFeedItem> parseRow(ResultSet result) {
        try {
            LinkFeedItem item = new LinkFeedItem();
            item.setId(result.getString("id"));
            item.setLinkName(result.getString("link_name"));
            item.setFeedUrl(result.getString("feed_url"));
            item.setGuid(result.getString("guid"));
            item.setUrl(result.getString("url"));
            item.setTitle(result.getString("title"));
            item.setSummary(result.getString("summary"));
            item.setAuthor(result.getString("author"));
            item.setPublishedAt(parseInstant(result.getString("published_at")));
            item.setUpdatedAt(parseInstant(result.getString("updated_at")));
            item.setFetchedAt(parseInstant(result.getString("fetched_at")));
            Instant firstSeenAt = parseInstant(result.getString("first_seen_at"));
            item.setFirstSeenAt(Optional.ofNullable(firstSeenAt).orElse(item.getFetchedAt()));
            item.setContentHash(result.getString("content_hash"));
            item.setRead(result.getInt("read") == 1);
            item.setFavorite(result.getInt("favorite") == 1);
            item.setReadLater(result.getInt("read_later") == 1);
            item.setHidden(result.getInt("hidden") == 1);
            return Optional.of(item);
        } catch (Exception e) {
            log.warn("[plugin-links] Failed to parse RSS SQLite row", e);
            return Optional.empty();
        }
    }

    private static void appendBooleanFilter(StringBuilder sql, List<Object> params,
        String column, Boolean value) {
        if (value != null) {
            sql.append(" AND ").append(column).append(" = ?");
            params.add(value ? 1 : 0);
        }
    }

    private static Comparator<LinkFeedItem> recentComparator() {
        return Comparator.comparing(SqliteLinkFeedItemStore::sortInstant,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .reversed()
            .thenComparing(LinkFeedItem::getId,
                Comparator.nullsLast(Comparator.reverseOrder()));
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
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String toString(Instant instant) {
        return instant == null ? null : SQLITE_INSTANT_FORMATTER.format(instant);
    }

    private static void validateItem(LinkFeedItem item) {
        if (item == null || !StringUtils.hasText(item.getId())) {
            throw new IllegalArgumentException("Feed item id must not be blank.");
        }
    }

    private static LinkedHashSet<String> validateHiddenIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Feed item ids must not be empty.");
        }
        LinkedHashSet<String> distinctIds = new LinkedHashSet<>();
        for (String id : ids) {
            if (!StringUtils.hasText(id)) {
                throw new IllegalArgumentException("Feed item ids must not contain blank values.");
            }
            distinctIds.add(id);
        }
        return distinctIds;
    }
}
