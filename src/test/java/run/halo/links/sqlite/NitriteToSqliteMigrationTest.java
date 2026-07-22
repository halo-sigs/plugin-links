package run.halo.links.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.dizitart.no2.collection.Document.createDocument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.mvstore.MVStoreModule;
import org.dizitart.no2.support.exchange.ExportOptions;
import org.dizitart.no2.support.exchange.Exporter;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import run.halo.links.rss.LinkFeedItem;
import run.halo.links.rss.LinkFeedItemQuery;

@ExtendWith(OutputCaptureExtension.class)
class NitriteToSqliteMigrationTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldMigrateEveryFieldAndKeepLegacyFileUnchanged() throws IOException {
        Path legacyPath = tempDir.resolve("links.nitrite");
        createLegacyDatabase(legacyPath);
        byte[] legacyBytes = Files.readAllBytes(legacyPath);

        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            SqliteLinkFeedItemStore store = new SqliteLinkFeedItemStore(database);
            assertThat(store.listRecent(new LinkFeedItemQuery()))
                .extracting(LinkFeedItem::getId)
                .containsExactly("legacy-b", "legacy-a");
            assertThat(store.listRecent(new LinkFeedItemQuery()))
                .filteredOn(item -> item.getId().equals("legacy-a"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getLinkName()).isEqualTo("link-a");
                    assertThat(item.getFeedUrl()).isEqualTo("https://example.com/feed.xml");
                    assertThat(item.getGuid()).isEqualTo("guid-a");
                    assertThat(item.getUrl()).isEqualTo("https://example.com/a");
                    assertThat(item.getTitle()).isEqualTo("Title A");
                    assertThat(item.getSummary()).isEqualTo("Summary A");
                    assertThat(item.getAuthor()).isEqualTo("Author A");
                    assertThat(item.getPublishedAt())
                        .isEqualTo(Instant.parse("2026-05-20T10:00:00.123456789Z"));
                    assertThat(item.getUpdatedAt())
                        .isEqualTo(Instant.parse("2026-05-20T11:00:00Z"));
                    assertThat(item.getFirstSeenAt())
                        .isEqualTo(Instant.parse("2026-05-21T12:00:00Z"));
                    assertThat(item.getFetchedAt())
                        .isEqualTo(Instant.parse("2026-05-22T12:00:00Z"));
                    assertThat(item.getContentHash()).isEqualTo("hash-a");
                    assertThat(item.getRead()).isTrue();
                    assertThat(item.getFavorite()).isTrue();
                    assertThat(item.getReadLater()).isTrue();
                });
            assertThat(Files.readAllBytes(legacyPath)).isEqualTo(legacyBytes);
            assertThat(Files.exists(database.migrationMarkerPath())).isTrue();
            assertThat(snapshotFiles()).hasSize(1);
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldSkipUnreadableRecordAndMigrateRemainingRecords() {
        Path legacyPath = tempDir.resolve("links.nitrite");
        try (Nitrite legacy = openLegacy(legacyPath)) {
            legacy.getCollection("link-feed-items").insert(
                createDocument("id", "good")
                    .put("publishedAt", "2026-05-20T10:00:00Z"),
                createDocument("id", "bad")
                    .put("publishedAt", 42));
            legacy.commit();
        }

        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            SqliteLinkFeedItemStore store = new SqliteLinkFeedItemStore(database);
            assertThat(store.listRecent(new LinkFeedItemQuery()))
                .extracting(LinkFeedItem::getId)
                .containsExactly("good");
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldKeepReadableRecordAndLogMalformedTimestamp(CapturedOutput output) {
        Path legacyPath = tempDir.resolve("links.nitrite");
        try (Nitrite legacy = openLegacy(legacyPath)) {
            legacy.getCollection("link-feed-items").insert(
                createDocument("id", "degraded-time")
                    .put("publishedAt", "not-an-instant"));
            legacy.commit();
        }

        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            assertThat(new SqliteLinkFeedItemStore(database)
                .listRecent(new LinkFeedItemQuery()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getId()).isEqualTo("degraded-time");
                    assertThat(item.getPublishedAt()).isNull();
                });
            assertThat(output)
                .contains("id=degraded-time")
                .contains("field=publishedAt")
                .contains("value=not-an-instant");
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldCreateEmptyDatabaseWhenLegacyDatabaseIsUnreadable() throws IOException {
        Path legacyPath = tempDir.resolve("links.nitrite");
        Files.writeString(legacyPath, "not a Nitrite database");

        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            assertThat(new SqliteLinkFeedItemStore(database).count()).isZero();
            assertThat(Files.exists(database.migrationMarkerPath())).isTrue();
            assertThat(Files.exists(legacyPath)).isFalse();
            try (var files = Files.list(tempDir)) {
                assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .anyMatch(name -> name.startsWith("links.nitrite.corrupt-"));
            }
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldUseNewestReadableJsonBackupWhenLiveDatabaseIsUnreadable() throws IOException {
        Path legacyPath = tempDir.resolve("links.nitrite");
        createLegacyDatabase(legacyPath);
        Path validBackup = tempDir.resolve("links.nitrite.bak-202607220100.json");
        exportLegacyDatabase(legacyPath, validBackup);
        Path invalidNewerBackup = tempDir.resolve("links.nitrite.bak-202607220200.json");
        Files.writeString(invalidNewerBackup, "not a Nitrite export");
        Files.writeString(legacyPath, "not a Nitrite database");

        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            assertThat(new SqliteLinkFeedItemStore(database).count()).isEqualTo(2);
            assertThat(Files.exists(legacyPath)).isFalse();
            assertThat(Files.exists(validBackup)).isTrue();
            assertThat(Files.exists(invalidNewerBackup)).isTrue();
            try (var files = Files.list(tempDir)) {
                assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .anyMatch(name -> name.startsWith("links.nitrite.corrupt-"))
                    .doesNotContain("links.nitrite.migration-import.tmp");
            }
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldUseJsonBackupWhenLiveDatabaseIsMissing() throws IOException {
        Path legacyPath = tempDir.resolve("links.nitrite");
        createLegacyDatabase(legacyPath);
        Path backup = tempDir.resolve("links.nitrite.bak-202607220100.json");
        exportLegacyDatabase(legacyPath, backup);
        Files.delete(legacyPath);

        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            assertThat(new SqliteLinkFeedItemStore(database).count()).isEqualTo(2);
            assertThat(Files.exists(backup)).isTrue();
            assertThat(Files.exists(database.migrationMarkerPath())).isTrue();
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldCreateSelfContainedRollbackJournalMigrationDatabase() throws Exception {
        Path migrationPath = tempDir.resolve("links.sqlite.migration.tmp");
        try (var connection = LinksSqliteDatabase.openMigrationDatabase(migrationPath);
            var statement = connection.createStatement();
            var result = statement.executeQuery("PRAGMA journal_mode")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualToIgnoringCase("delete");
        }

        assertThat(Files.exists(migrationPath)).isTrue();
        assertThat(Files.exists(tempDir.resolve("links.sqlite.migration.tmp-wal"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("links.sqlite.migration.tmp-shm"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("links.sqlite.migration.tmp-journal"))).isFalse();
    }

    @Test
    void shouldMigrateReadableDatabaseWithoutRssCollectionAsEmpty() throws IOException {
        Path legacyPath = tempDir.resolve("links.nitrite");
        try (Nitrite legacy = openLegacy(legacyPath)) {
            legacy.getCollection("unrelated").insert(createDocument("id", "unrelated-item"));
            legacy.commit();
        }
        byte[] legacyBytes = Files.readAllBytes(legacyPath);

        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            assertThat(new SqliteLinkFeedItemStore(database).count()).isZero();
            assertThat(Files.exists(legacyPath)).isTrue();
            assertThat(Files.readAllBytes(legacyPath)).isEqualTo(legacyBytes);
            assertThat(Files.exists(database.migrationMarkerPath())).isTrue();
            assertThat(snapshotFiles()).hasSize(1);
            try (var files = Files.list(tempDir)) {
                assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .noneMatch(name -> name.startsWith("links.nitrite.corrupt-"));
            }
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldMigrateSameSourceIdentityAcrossFeedsAndTimestampPrecisions() {
        Path legacyPath = tempDir.resolve("links.nitrite");
        try (Nitrite legacy = openLegacy(legacyPath)) {
            legacy.getCollection("link-feed-items").insert(
                createDocument("id", "precision-0")
                    .put("feedUrl", "https://example.com/0.xml")
                    .put("guid", "shared-guid")
                    .put("publishedAt", "2026-05-20T10:00:00Z"),
                createDocument("id", "precision-3")
                    .put("feedUrl", "https://example.com/3.xml")
                    .put("guid", "shared-guid")
                    .put("publishedAt", "2026-05-20T10:00:00.123Z"),
                createDocument("id", "precision-6")
                    .put("feedUrl", "https://example.com/6.xml")
                    .put("guid", "shared-guid")
                    .put("publishedAt", "2026-05-20T10:00:00.123456Z"),
                createDocument("id", "precision-9")
                    .put("feedUrl", "https://example.com/9.xml")
                    .put("guid", "shared-guid")
                    .put("publishedAt", "2026-05-20T10:00:00.123456789Z"));
            legacy.commit();
        }

        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            assertThat(new SqliteLinkFeedItemStore(database)
                .listRecent(new LinkFeedItemQuery()))
                .extracting(LinkFeedItem::getId, LinkFeedItem::getFeedUrl,
                    LinkFeedItem::getGuid, item -> item.getPublishedAt().toString())
                .containsExactlyInAnyOrder(
                    tuple("precision-0", "https://example.com/0.xml", "shared-guid",
                        "2026-05-20T10:00:00Z"),
                    tuple("precision-3", "https://example.com/3.xml", "shared-guid",
                        "2026-05-20T10:00:00.123Z"),
                    tuple("precision-6", "https://example.com/6.xml", "shared-guid",
                        "2026-05-20T10:00:00.123456Z"),
                    tuple("precision-9", "https://example.com/9.xml", "shared-guid",
                        "2026-05-20T10:00:00.123456789Z"));
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldRecreateExternalMarkerFromInternalMetadata() throws IOException {
        Path dbPath = tempDir.resolve("links.sqlite");
        createLegacyDatabase(tempDir.resolve("links.nitrite"));
        LinksSqliteDatabase first = new LinksSqliteDatabase(dbPath);
        Path marker = first.migrationMarkerPath();
        first.destroy();
        Files.delete(marker);

        LinksSqliteDatabase restarted = new LinksSqliteDatabase(dbPath);
        try {
            assertThat(Files.exists(marker)).isTrue();
            assertThat(new SqliteLinkFeedItemStore(restarted).count()).isEqualTo(2);
        } finally {
            restarted.destroy();
        }
    }

    @Test
    void shouldNotReimportLegacyDatabaseAfterCompletedMigration() throws IOException {
        Path dbPath = tempDir.resolve("links.sqlite");
        Path legacyPath = tempDir.resolve("links.nitrite");
        createLegacyDatabase(legacyPath);
        LinksSqliteDatabase first = new LinksSqliteDatabase(dbPath);
        first.destroy();

        try (Nitrite legacy = openLegacy(legacyPath)) {
            legacy.getCollection("link-feed-items").insert(
                createDocument("id", "stale-new")
                    .put("publishedAt", "2026-05-30T10:00:00Z"));
            legacy.commit();
        }
        Files.delete(dbPath);

        LinksSqliteDatabase restarted = new LinksSqliteDatabase(dbPath);
        try {
            assertThat(new SqliteLinkFeedItemStore(restarted)
                .listRecent(new LinkFeedItemQuery()))
                .extracting(LinkFeedItem::getId)
                .containsExactly("legacy-b", "legacy-a")
                .doesNotContain("stale-new");
        } finally {
            restarted.destroy();
        }
    }

    @Test
    void shouldLeaveLegacyUntouchedWhenTemporaryDatabaseCannotBeCreated() throws IOException {
        Path legacyPath = tempDir.resolve("links.nitrite");
        createLegacyDatabase(legacyPath);
        byte[] before = Files.readAllBytes(legacyPath);
        Path unavailableTempPath = tempDir.resolve("missing").resolve("links.sqlite.tmp");

        assertThatThrownBy(() -> NitriteToSqliteMigration.migrate(
            legacyPath, unavailableTempPath, Clock.systemUTC()))
            .isInstanceOf(Exception.class);
        assertThat(Files.readAllBytes(legacyPath)).isEqualTo(before);
        assertThat(Files.exists(tempDir.resolve("links.sqlite"))).isFalse();

        LinksSqliteDatabase retry = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            assertThat(new SqliteLinkFeedItemStore(retry).count()).isEqualTo(2);
        } finally {
            retry.destroy();
        }
    }

    private List<Path> snapshotFiles() throws IOException {
        try (var files = Files.list(tempDir)) {
            return files.filter(path -> path.getFileName().toString()
                    .startsWith("links.sqlite.snapshot-"))
                .toList();
        }
    }

    private static void createLegacyDatabase(Path path) {
        try (Nitrite legacy = openLegacy(path)) {
            legacy.getCollection("link-feed-items").insert(
                createDocument("id", "legacy-a")
                    .put("linkName", "link-a")
                    .put("feedUrl", "https://example.com/feed.xml")
                    .put("guid", "guid-a")
                    .put("url", "https://example.com/a")
                    .put("title", "Title A")
                    .put("summary", "Summary A")
                    .put("author", "Author A")
                    .put("publishedAt", "2026-05-20T10:00:00.123456789Z")
                    .put("updatedAt", "2026-05-20T11:00:00Z")
                    .put("firstSeenAt", "2026-05-21T12:00:00Z")
                    .put("fetchedAt", "2026-05-22T12:00:00Z")
                    .put("contentHash", "hash-a")
                    .put("read", true)
                    .put("favorite", true)
                    .put("readLater", true),
                createDocument("id", "legacy-b")
                    .put("linkName", "link-a")
                    .put("feedUrl", "https://example.com/comments.xml")
                    .put("publishedAt", "2026-05-21T10:00:00Z")
                    .put("fetchedAt", "2026-05-22T12:00:00Z"));
            legacy.commit();
        }
    }

    private static void exportLegacyDatabase(Path source, Path target) throws IOException {
        ExportOptions options = new ExportOptions();
        options.setNitriteFactory(() -> openLegacy(source));
        Exporter.withOptions(options).exportTo(target.toFile());
    }

    private static Nitrite openLegacy(Path path) {
        return Nitrite.builder()
            .loadModule(MVStoreModule.withConfig().filePath(path.toFile()).build())
            .openOrCreate();
    }
}
