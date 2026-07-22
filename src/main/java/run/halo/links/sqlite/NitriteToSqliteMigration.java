package run.halo.links.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.mvstore.MVStoreModule;
import org.dizitart.no2.support.exchange.ImportOptions;
import org.dizitart.no2.support.exchange.Importer;
import org.springframework.util.StringUtils;
import run.halo.links.rss.LinkFeedItem;

@Slf4j
final class NitriteToSqliteMigration {

    private static final String LEGACY_COLLECTION = "link-feed-items";
    private static final Pattern LEGACY_BACKUP_NAME =
        Pattern.compile("links\\.nitrite\\.bak-\\d{12}\\.json");

    private NitriteToSqliteMigration() {
    }

    static boolean hasLegacySource(Path legacyDbPath) throws IOException {
        return Files.isRegularFile(legacyDbPath) || !jsonBackups(legacyDbPath).isEmpty();
    }

    static Result migrate(Path legacyDbPath, Path sqliteTempPath, Clock clock)
        throws IOException, SQLException {
        Instant migrationTime = Instant.now(clock);
        LegacyItems source = readFirstAvailable(legacyDbPath, migrationTime);
        if (source == null) {
            return Result.SOURCE_UNREADABLE;
        }

        Files.deleteIfExists(sqliteTempPath);
        try (Connection connection = LinksSqliteDatabase.openMigrationDatabase(sqliteTempPath)) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                SqliteLinkFeedItemStore.upsertAll(connection,
                    List.copyOf(source.items().values()));
                LinksSqliteDatabase.markMigrationComplete(connection);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }

        if (!LinksSqliteDatabase.isValidStandaloneDatabase(sqliteTempPath)) {
            throw new SQLException("Migrated SQLite database failed quick_check");
        }
        long inserted = countRows(sqliteTempPath);
        if (inserted != source.items().size()) {
            throw new SQLException("Migrated row count mismatch: expected=" + source.items().size()
                + ", actual=" + inserted);
        }
        log.info("[plugin-links] Prepared {} RSS item(s) for Nitrite-to-SQLite migration from {}",
            inserted, source.path());
        return source.fromBackup() ? Result.MIGRATED_FROM_BACKUP : Result.MIGRATED;
    }

    private static LegacyItems readFirstAvailable(Path legacyDbPath, Instant migrationTime)
        throws IOException {
        if (Files.isRegularFile(legacyDbPath)) {
            try {
                return new LegacyItems(readItems(legacyDbPath, migrationTime, false), legacyDbPath,
                    false);
            } catch (Exception e) {
                log.error("[plugin-links] Legacy Nitrite RSS database is unreadable: path={}; "
                    + "trying JSON backups", legacyDbPath, e);
            }
        }
        for (Path backup : jsonBackups(legacyDbPath)) {
            try {
                return new LegacyItems(readJsonBackup(legacyDbPath, backup, migrationTime), backup,
                    true);
            } catch (Exception e) {
                log.error("[plugin-links] Legacy Nitrite RSS backup is unreadable: path={}; "
                    + "trying next backup", backup, e);
            }
        }
        return null;
    }

    private static Map<String, LinkFeedItem> readItems(Path source, Instant migrationTime,
        boolean importedBackup) {
        Map<String, LinkFeedItem> items = new LinkedHashMap<>();
        var config = MVStoreModule.withConfig().filePath(source.toFile());
        if (!importedBackup) {
            config.readOnly(true);
        }
        try (Nitrite database = Nitrite.builder()
            .loadModule(config.build())
            .openOrCreate()) {
            if (importedBackup || database.hasCollection(LEGACY_COLLECTION)) {
                database.getCollection(LEGACY_COLLECTION).find().forEach(document ->
                    toItem(document, migrationTime)
                        .ifPresent(item -> items.put(item.getId(), item)));
            }
        }
        return items;
    }

    private static Map<String, LinkFeedItem> readJsonBackup(Path legacyDbPath, Path backup,
        Instant migrationTime) throws IOException {
        Path imported = legacyDbPath.resolveSibling(
            legacyDbPath.getFileName() + ".migration-import.tmp");
        Files.deleteIfExists(imported);
        try {
            ImportOptions options = new ImportOptions();
            options.setNitriteFactory(() -> openWritableNitrite(imported));
            Importer.withOptions(options).importFrom(backup.toFile());
            return readItems(imported, migrationTime, true);
        } finally {
            deleteQuietly(imported);
        }
    }

    private static Nitrite openWritableNitrite(Path path) {
        return Nitrite.builder()
            .loadModule(MVStoreModule.withConfig()
                .filePath(path.toFile())
                .compress(true)
                .build())
            .openOrCreate();
    }

    private static List<Path> jsonBackups(Path legacyDbPath) throws IOException {
        Path directory = legacyDbPath.getParent();
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files
                .filter(path -> !Files.isSymbolicLink(path))
                .filter(Files::isRegularFile)
                .filter(path -> LEGACY_BACKUP_NAME.matcher(
                    path.getFileName().toString()).matches())
                .sorted(Comparator.comparing((Path path) -> path.getFileName().toString())
                    .reversed())
                .toList();
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("[plugin-links] Failed to delete temporary imported Nitrite database at {}",
                path, e);
        }
    }

    private static long countRows(Path sqlitePath) throws SQLException {
        try (Connection connection = LinksSqliteDatabase.openStandaloneReadOnly(sqlitePath);
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("SELECT count(*) FROM link_feed_items")) {
            return result.next() ? result.getLong(1) : 0;
        }
    }

    private static Optional<LinkFeedItem> toItem(Document document, Instant migrationTime) {
        String id = null;
        try {
            id = document.get("id", String.class);
            if (!StringUtils.hasText(id)) {
                log.error("[plugin-links] Skipping legacy RSS item without an id");
                return Optional.empty();
            }
            LinkFeedItem item = new LinkFeedItem();
            item.setId(id);
            item.setLinkName(document.get("linkName", String.class));
            item.setFeedUrl(document.get("feedUrl", String.class));
            item.setGuid(document.get("guid", String.class));
            item.setUrl(document.get("url", String.class));
            item.setTitle(document.get("title", String.class));
            item.setSummary(document.get("summary", String.class));
            item.setAuthor(document.get("author", String.class));
            item.setPublishedAt(parseInstant(document.get("publishedAt", String.class), id,
                "publishedAt"));
            item.setUpdatedAt(parseInstant(document.get("updatedAt", String.class), id,
                "updatedAt"));
            item.setFetchedAt(parseInstant(document.get("fetchedAt", String.class), id,
                "fetchedAt"));
            Instant firstSeenAt = parseInstant(document.get("firstSeenAt", String.class), id,
                "firstSeenAt");
            if (firstSeenAt == null) {
                firstSeenAt = Optional.ofNullable(item.getFetchedAt()).orElse(migrationTime);
            }
            item.setFirstSeenAt(firstSeenAt);
            item.setContentHash(document.get("contentHash", String.class));
            item.setRead(Boolean.TRUE.equals(document.get("read", Boolean.class)));
            item.setFavorite(Boolean.TRUE.equals(document.get("favorite", Boolean.class)));
            item.setReadLater(Boolean.TRUE.equals(document.get("readLater", Boolean.class)));
            return Optional.of(item);
        } catch (Exception e) {
            log.error("[plugin-links] Skipping unreadable legacy RSS item: id={}", id, e);
            return Optional.empty();
        }
    }

    private static Instant parseInstant(String value, String id, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            log.error("[plugin-links] Legacy RSS item timestamp is invalid: id={}, field={}, "
                + "value={}", id, field, value);
            return null;
        }
    }

    enum Result {
        MIGRATED,
        MIGRATED_FROM_BACKUP,
        SOURCE_UNREADABLE
    }

    private record LegacyItems(Map<String, LinkFeedItem> items, Path path, boolean fromBackup) {
    }
}
