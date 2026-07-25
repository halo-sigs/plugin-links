package run.halo.links.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import run.halo.links.rss.LinkFeedItem;
import run.halo.links.rss.LinkFeedItemQuery;

class LinksSqliteRecoveryTest {

    private static final DateTimeFormatter FILE_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @Test
    void shouldRestoreActiveDatabaseFromNewestValidSnapshot() throws IOException {
        Path dbPath = tempDir.resolve("links.sqlite");
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T00:00:00Z"));
        LinksSqliteDatabase database = new LinksSqliteDatabase(dbPath, clock);
        new SqliteLinkFeedItemStore(database).upsert(item("snapshot-item"));
        database.createSnapshot();
        database.destroy();
        Files.writeString(dbPath, "corrupted");

        LinksSqliteDatabase restored = new LinksSqliteDatabase(dbPath, clock);
        try {
            assertThat(new SqliteLinkFeedItemStore(restored)
                .listRecent(new LinkFeedItemQuery()))
                .extracting(LinkFeedItem::getId)
                .containsExactly("snapshot-item");
            assertThat(quarantinedMainFiles()).hasSize(1);
        } finally {
            restored.destroy();
        }
    }

    @Test
    void shouldTryOlderSnapshotWhenNewestIsInvalid() throws IOException {
        Path dbPath = tempDir.resolve("links.sqlite");
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T00:00:00Z"));
        LinksSqliteDatabase database = new LinksSqliteDatabase(dbPath, clock);
        SqliteLinkFeedItemStore store = new SqliteLinkFeedItemStore(database);
        store.upsert(item("older"));
        database.createSnapshot();
        clock.advance(Duration.ofDays(1));
        store.upsert(item("newer"));
        database.createSnapshot();
        database.destroy();
        Files.writeString(dbPath, "corrupted active");
        Files.writeString(snapshotFiles().get(0), "corrupted snapshot");

        LinksSqliteDatabase restored = new LinksSqliteDatabase(dbPath, clock);
        try {
            assertThat(new SqliteLinkFeedItemStore(restored)
                .listRecent(new LinkFeedItemQuery()))
                .extracting(LinkFeedItem::getId)
                .containsExactly("older");
        } finally {
            restored.destroy();
        }
    }

    @Test
    void shouldCreateEmptyDatabaseWhenAllRecoveryCandidatesAreInvalid() throws IOException {
        Path dbPath = tempDir.resolve("links.sqlite");
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T00:00:00Z"));
        LinksSqliteDatabase database = new LinksSqliteDatabase(dbPath, clock);
        new SqliteLinkFeedItemStore(database).upsert(item("discarded"));
        database.createSnapshot();
        database.destroy();
        Files.writeString(dbPath, "corrupted active");
        Files.writeString(snapshotFiles().get(0), "corrupted snapshot");
        Files.writeString(tempDir.resolve("links.sqlite-wal"), "stale wal");
        Files.writeString(tempDir.resolve("links.sqlite-shm"), "stale shm");

        LinksSqliteDatabase rebuilt = new LinksSqliteDatabase(dbPath, clock);
        try {
            assertThat(new SqliteLinkFeedItemStore(rebuilt).count()).isZero();
            assertThat(quarantinedMainFiles()).hasSize(1);
            try (var files = Files.list(tempDir)) {
                List<String> names = files.map(path -> path.getFileName().toString()).toList();
                assertThat(names).anyMatch(name -> name.endsWith("-wal"));
                assertThat(names).anyMatch(name -> name.endsWith("-shm"));
            }
        } finally {
            rebuilt.destroy();
        }
    }

    @Test
    void shouldRestoreSnapshotWhenActiveDatabaseIsMissing() throws IOException {
        Path dbPath = tempDir.resolve("links.sqlite");
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T00:00:00Z"));
        LinksSqliteDatabase database = new LinksSqliteDatabase(dbPath, clock);
        new SqliteLinkFeedItemStore(database).upsert(item("restored"));
        database.createSnapshot();
        database.destroy();
        Files.delete(dbPath);

        LinksSqliteDatabase restored = new LinksSqliteDatabase(dbPath, clock);
        try {
            assertThat(new SqliteLinkFeedItemStore(restored).count()).isOne();
        } finally {
            restored.destroy();
        }
    }

    @Test
    void shouldKeepTwoNewestValidatedSnapshots() throws IOException {
        Path dbPath = tempDir.resolve("links.sqlite");
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T00:00:00Z"));
        LinksSqliteDatabase database = new LinksSqliteDatabase(dbPath, clock);
        try {
            SqliteLinkFeedItemStore store = new SqliteLinkFeedItemStore(database);
            for (int i = 0; i < 3; i++) {
                store.upsert(item("item-" + i));
                database.createSnapshot();
                clock.advance(Duration.ofDays(1));
            }
            assertThat(snapshotFiles()).hasSize(2);
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldReplaceDamagedNewestSnapshotWithoutRemovingOlderValidSnapshot()
        throws IOException {
        Path dbPath = tempDir.resolve("links.sqlite");
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T00:00:00Z"));
        LinksSqliteDatabase database = new LinksSqliteDatabase(dbPath, clock);
        try {
            database.createSnapshot();
            Path olderValid = snapshotFiles().get(0);

            clock.advance(Duration.ofDays(1));
            database.createSnapshot();
            Path damagedNewest = snapshotFiles().get(0);
            Files.writeString(damagedNewest, "damaged snapshot");

            clock.advance(Duration.ofHours(1));
            database.snapshotIfDue();

            assertThat(snapshotFiles()).hasSize(2).contains(olderValid);
            assertThat(snapshotFiles()).doesNotContain(damagedNewest);
            assertThat(snapshotFiles()).allMatch(LinksSqliteDatabase::isValidStandaloneDatabase);
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldValidateStandaloneSnapshotWithoutWritingFiles() throws IOException {
        Path dbPath = tempDir.resolve("links.sqlite");
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T00:00:00Z"));
        LinksSqliteDatabase database = new LinksSqliteDatabase(dbPath, clock);
        new SqliteLinkFeedItemStore(database).upsert(item("snapshot-item"));
        database.createSnapshot();
        database.destroy();
        Path snapshot = snapshotFiles().get(0);
        byte[] before = Files.readAllBytes(snapshot);

        assertThat(LinksSqliteDatabase.isValidStandaloneDatabase(snapshot)).isTrue();
        assertThatThrownBy(() -> {
            try (var connection = LinksSqliteDatabase.openStandaloneReadOnly(snapshot);
                var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO link_feed_items(id) VALUES ('forbidden')");
            }
        }).isInstanceOf(SQLException.class);
        assertThat(Files.readAllBytes(snapshot)).isEqualTo(before);
        assertThat(Files.exists(snapshot.resolveSibling(snapshot.getFileName() + "-wal")))
            .isFalse();
        assertThat(Files.exists(snapshot.resolveSibling(snapshot.getFileName() + "-shm")))
            .isFalse();
    }

    @Test
    void shouldCreateScheduledSnapshotOnlyAfterDailyInterval() throws IOException {
        Path dbPath = tempDir.resolve("links.sqlite");
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T00:00:00Z"));
        LinksSqliteDatabase database = new LinksSqliteDatabase(dbPath, clock);
        try {
            database.snapshotIfDue();
            assertThat(snapshotFiles()).hasSize(1);

            clock.advance(Duration.ofHours(23));
            database.snapshotIfDue();
            assertThat(snapshotFiles()).hasSize(1);

            clock.advance(Duration.ofHours(1));
            database.snapshotIfDue();
            assertThat(snapshotFiles()).hasSize(2);
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldKeepExistingSnapshotWhenNewSnapshotFails() throws IOException {
        Path dbPath = tempDir.resolve("links.sqlite");
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T00:00:00Z"));
        LinksSqliteDatabase database = new LinksSqliteDatabase(dbPath, clock);
        try {
            database.createSnapshot();
            clock.advance(Duration.ofDays(1));
            Path failedTemp = tempDir.resolve("links.sqlite.snapshot-"
                + FILE_TIMESTAMP.format(clock.instant()) + ".tmp");
            Files.createDirectory(failedTemp);
            Files.writeString(failedTemp.resolve("block-delete"), "x");

            database.createSnapshot();

            assertThat(database.isAvailable()).isTrue();
            assertThat(snapshotFiles()).hasSize(1);
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldRetainOnlyNewestQuarantinedDatabaseGroup() throws IOException {
        Path dbPath = tempDir.resolve("links.sqlite");
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T00:00:00Z"));
        LinksSqliteDatabase first = new LinksSqliteDatabase(dbPath, clock);
        first.destroy();
        Files.writeString(dbPath, "first corruption");
        LinksSqliteDatabase firstRecovery = new LinksSqliteDatabase(dbPath, clock);
        firstRecovery.destroy();

        clock.advance(Duration.ofDays(1));
        Files.writeString(dbPath, "second corruption");
        LinksSqliteDatabase secondRecovery = new LinksSqliteDatabase(dbPath, clock);
        try {
            assertThat(quarantinedMainFiles()).hasSize(1);
        } finally {
            secondRecovery.destroy();
        }
    }

    private List<Path> snapshotFiles() throws IOException {
        try (var files = Files.list(tempDir)) {
            return files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString()
                    .startsWith("links.sqlite.snapshot-"))
                .filter(path -> !path.getFileName().toString().endsWith("-wal"))
                .filter(path -> !path.getFileName().toString().endsWith("-shm"))
                .sorted((left, right) -> right.getFileName().toString()
                    .compareTo(left.getFileName().toString()))
                .toList();
        }
    }

    private List<Path> quarantinedMainFiles() throws IOException {
        try (var files = Files.list(tempDir)) {
            return files.filter(path -> path.getFileName().toString()
                    .startsWith("links.sqlite.corrupt-"))
                .filter(path -> !path.getFileName().toString().endsWith("-wal"))
                .filter(path -> !path.getFileName().toString().endsWith("-shm"))
                .toList();
        }
    }

    private static LinkFeedItem item(String id) {
        LinkFeedItem item = new LinkFeedItem();
        item.setId(id);
        item.setLinkName("link-a");
        item.setFeedUrl("https://example.com/feed.xml");
        item.setPublishedAt(Instant.parse("2026-07-20T00:00:00Z"));
        item.setFirstSeenAt(Instant.parse("2026-07-20T00:00:00Z"));
        item.setFetchedAt(Instant.parse("2026-07-20T00:00:00Z"));
        return item;
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
