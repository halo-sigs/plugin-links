package run.halo.links.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteConfig;
import run.halo.links.rss.LinkFeedItem;
import run.halo.links.rss.LinkFeedStorageUnavailableException;

class LinksSqliteDatabaseTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateConfiguredVersionedDatabase() {
        LinksSqliteDatabase database = database();
        try {
            assertThat(pragma(database, "user_version")).isEqualTo("1");
            assertThat(pragma(database, "journal_mode")).isEqualToIgnoringCase("wal");
            assertThat(pragma(database, "synchronous")).isEqualTo("2");
            assertThat(pragma(database, "busy_timeout")).isEqualTo("5000");
            assertThat(columnExists(database, "link_feed_items", "hidden")).isTrue();
            assertThat(indexExists(database, "idx_feed_items_hidden_recent")).isTrue();
            assertThat(database.isAvailable()).isTrue();
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldAddHiddenColumnToExistingVersionOneDatabaseAndPreserveRows() throws Exception {
        Path dbPath = tempDir.resolve("links.sqlite");
        createVersionOneDatabaseWithoutHidden(dbPath, "existing-item");

        LinksSqliteDatabase database = new LinksSqliteDatabase(dbPath);
        try {
            assertThat(database.isAvailable()).isTrue();
            assertThat(pragma(database, "user_version")).isEqualTo("1");
            assertThat(columnExists(database, "link_feed_items", "hidden")).isTrue();
            int hidden = database.execute(connection -> {
                try (var statement = connection.createStatement();
                    var result = statement.executeQuery(
                        "SELECT hidden FROM link_feed_items WHERE id = 'existing-item'")) {
                    return result.next() ? result.getInt(1) : -1;
                }
            });
            assertThat(hidden).isZero();
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldPreserveHiddenStateAcrossRepeatedStartup() {
        Path dbPath = tempDir.resolve("links.sqlite");
        LinksSqliteDatabase first = new LinksSqliteDatabase(dbPath);
        new SqliteLinkFeedItemStore(first).upsert(item("hidden-item"));
        new SqliteLinkFeedItemStore(first).updateHidden(List.of("hidden-item"), true);
        first.destroy();

        LinksSqliteDatabase restarted = new LinksSqliteDatabase(dbPath);
        try {
            assertThat(restarted.isAvailable()).isTrue();
            assertThat(new SqliteLinkFeedItemStore(restarted).countHidden()).isOne();
            assertThat(pragma(restarted, "user_version")).isEqualTo("1");
        } finally {
            restarted.destroy();
        }
    }

    @Test
    void shouldUpgradeRestoredVersionOneSnapshotWithoutHiddenColumn() throws Exception {
        Path dbPath = tempDir.resolve("links.sqlite");
        Path snapshot = tempDir.resolve("links.sqlite.snapshot-20260801000000000");
        createVersionOneDatabaseWithoutHidden(snapshot, "snapshot-item");

        LinksSqliteDatabase database = new LinksSqliteDatabase(dbPath);
        try {
            assertThat(database.isAvailable()).isTrue();
            assertThat(columnExists(database, "link_feed_items", "hidden")).isTrue();
            assertThat(new SqliteLinkFeedItemStore(database).count()).isOne();
            assertThat(new SqliteLinkFeedItemStore(database).countHidden()).isZero();
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldKeepDatabaseForRetryWhenHiddenIndexCreationFails() throws Exception {
        Path dbPath = tempDir.resolve("links.sqlite");
        createVersionOneDatabaseWithoutHidden(dbPath, "existing-item");
        try (Connection connection = openSqlite(dbPath);
            var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE idx_feed_items_hidden_recent (id TEXT)");
        }

        LinksSqliteDatabase failed = new LinksSqliteDatabase(dbPath);
        assertThat(failed.isAvailable()).isFalse();
        failed.destroy();
        assertThat(dbPath).exists();
        assertThat(countRows(dbPath, "link_feed_items")).isOne();

        try (Connection connection = openSqlite(dbPath);
            var statement = connection.createStatement()) {
            statement.execute("DROP TABLE idx_feed_items_hidden_recent");
        }
        LinksSqliteDatabase retried = new LinksSqliteDatabase(dbPath);
        try {
            assertThat(retried.isAvailable()).isTrue();
            assertThat(new SqliteLinkFeedItemStore(retried).count()).isOne();
            assertThat(indexExists(retried, "idx_feed_items_hidden_recent")).isTrue();
        } finally {
            retried.destroy();
        }
    }

    @Test
    void shouldRemoveStaleNitriteImportFileOnStartup() throws Exception {
        Path staleImport = tempDir.resolve("links.nitrite.migration-import.tmp");
        Files.writeString(staleImport, "stale");

        LinksSqliteDatabase database = database();
        try {
            assertThat(staleImport).doesNotExist();
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldRollbackFailedTransaction() {
        LinksSqliteDatabase database = database();
        try {
            assertThatThrownBy(() -> database.inTransaction(connection -> {
                try (var statement = connection.prepareStatement(
                    "INSERT INTO link_feed_items(id) VALUES (?)")) {
                    statement.setString(1, "rolled-back");
                    statement.executeUpdate();
                }
                throw new SQLException("test failure");
            })).isInstanceOf(IllegalStateException.class);

            long count = database.execute(connection -> {
                try (var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT count(*) FROM link_feed_items")) {
                    return result.next() ? result.getLong(1) : -1;
                }
            });
            assertThat(count).isZero();
            assertThat(database.isAvailable()).isTrue();
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldSerializeConnectionCallbacks() throws Exception {
        LinksSqliteDatabase database = database();
        var executor = Executors.newFixedThreadPool(4);
        try {
            AtomicInteger active = new AtomicInteger();
            AtomicInteger maximum = new AtomicInteger();
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                futures.add(executor.submit(() -> database.execute(connection -> {
                    int current = active.incrementAndGet();
                    maximum.accumulateAndGet(current, Math::max);
                    try {
                        LockSupport.parkNanos(5_000_000L);
                    } finally {
                        active.decrementAndGet();
                    }
                    return null;
                })));
            }
            for (var future : futures) {
                future.get();
            }
            assertThat(maximum).hasValue(1);
        } finally {
            executor.shutdownNow();
            database.destroy();
        }
    }

    @Test
    void shouldRemainUnavailableAfterConnectionIsLostUntilRestart() {
        LinksSqliteDatabase database = database();
        try {
            database.execute(connection -> {
                connection.close();
                return null;
            });

            assertThatThrownBy(() -> database.execute(connection -> null))
                .isInstanceOf(LinkFeedStorageUnavailableException.class);
            assertThat(database.isAvailable()).isFalse();
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldReleaseAndRegisterPluginSqliteDriverAcrossLifecycles() {
        LinksSqliteDatabase first = new LinksSqliteDatabase(tempDir.resolve("first.sqlite"));
        LinksSqliteDatabase second = new LinksSqliteDatabase(tempDir.resolve("second.sqlite"));
        assertThat(pluginSqliteDriverCount()).isGreaterThanOrEqualTo(1);

        first.destroy();
        first.destroy();
        assertThat(pluginSqliteDriverCount()).isGreaterThanOrEqualTo(1);
        boolean secondConnectionValid = second.execute(connection -> connection.isValid(1));
        assertThat(secondConnectionValid).isTrue();

        second.destroy();
        assertThat(pluginSqliteDriverCount()).isZero();

        LinksSqliteDatabase reloaded = new LinksSqliteDatabase(tempDir.resolve("reloaded.sqlite"));
        try {
            assertThat(reloaded.isAvailable()).isTrue();
            assertThat(pluginSqliteDriverCount()).isGreaterThanOrEqualTo(1);
        } finally {
            reloaded.destroy();
        }
        assertThat(pluginSqliteDriverCount()).isZero();
    }

    @Test
    void shouldBecomeUnavailableAfterBoundedBusyRetries() throws Exception {
        Path dbPath = tempDir.resolve("links.sqlite");
        LinksSqliteDatabase database = new LinksSqliteDatabase(dbPath);
        database.execute(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA busy_timeout = 1");
            }
            return null;
        });
        try (var blocker = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.createStatement()) {
                statement.executeUpdate("INSERT INTO link_feed_items(id) VALUES ('blocker')");
            }

            SqliteLinkFeedItemStore store = new SqliteLinkFeedItemStore(database);
            assertThatThrownBy(() -> store.upsert(item("blocked")))
                .isInstanceOf(LinkFeedStorageUnavailableException.class);
            assertThat(database.isAvailable()).isFalse();
            blocker.rollback();
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldNotTreatConstraintFailureAsDatabaseCorruption() {
        LinksSqliteDatabase database = database();
        try {
            assertThatThrownBy(() -> database.inTransaction(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("""
                        INSERT INTO link_feed_items(id, read) VALUES ('invalid-state', 2)
                        """);
                }
                return null;
            })).isInstanceOf(IllegalStateException.class);
            assertThat(database.isAvailable()).isTrue();
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldBecomeUnavailableWhenDatabaseIsFull() {
        LinksSqliteDatabase database = database();
        try {
            database.execute(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("PRAGMA max_page_count = "
                        + pragmaLong(connection, "page_count"));
                }
                return null;
            });

            assertThatThrownBy(() -> database.inTransaction(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("""
                        INSERT INTO link_feed_items(id, summary)
                        VALUES ('fills-database', hex(randomblob(1048576)))
                        """);
                }
                return null;
            })).isInstanceOf(LinkFeedStorageUnavailableException.class);
            assertThat(database.isAvailable()).isFalse();
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldRecoverFromReadOnlyFailureAfterRestart() {
        Path dbPath = tempDir.resolve("links.sqlite");
        LinksSqliteDatabase database = new LinksSqliteDatabase(dbPath);
        database.execute(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA query_only = ON");
            }
            return null;
        });

        assertThatThrownBy(() -> new SqliteLinkFeedItemStore(database).upsert(item("read-only")))
            .isInstanceOf(LinkFeedStorageUnavailableException.class);
        assertThat(database.isAvailable()).isFalse();
        database.destroy();

        LinksSqliteDatabase restarted = new LinksSqliteDatabase(dbPath);
        try {
            assertThat(restarted.isAvailable()).isTrue();
            new SqliteLinkFeedItemStore(restarted).upsert(item("after-restart"));
            assertThat(new SqliteLinkFeedItemStore(restarted).count()).isOne();
        } finally {
            restarted.destroy();
        }
    }

    private LinksSqliteDatabase database() {
        return new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
    }

    private static String pragma(LinksSqliteDatabase database, String name) {
        return database.execute(connection -> {
            try (var statement = connection.createStatement();
                var result = statement.executeQuery("PRAGMA " + name)) {
                return result.next() ? result.getString(1) : null;
            }
        });
    }

    private static boolean columnExists(LinksSqliteDatabase database, String table,
        String column) {
        return database.execute(connection -> {
            try (var statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
                while (result.next()) {
                    if (column.equals(result.getString("name"))) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private static boolean indexExists(LinksSqliteDatabase database, String indexName) {
        return database.execute(connection -> {
            try (var statement = connection.prepareStatement(
                "SELECT count(*) FROM sqlite_master WHERE type = 'index' AND name = ?")) {
                statement.setString(1, indexName);
                try (var result = statement.executeQuery()) {
                    return result.next() && result.getInt(1) == 1;
                }
            }
        });
    }

    private static void createVersionOneDatabaseWithoutHidden(Path path, String itemId)
        throws SQLException {
        try (Connection connection = openSqlite(path);
            var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE link_feed_items (
                  id TEXT PRIMARY KEY,
                  link_name TEXT,
                  feed_url TEXT,
                  guid TEXT,
                  url TEXT,
                  title TEXT,
                  summary TEXT,
                  author TEXT,
                  published_at TEXT,
                  updated_at TEXT,
                  first_seen_at TEXT,
                  fetched_at TEXT,
                  content_hash TEXT,
                  read INTEGER NOT NULL DEFAULT 0 CHECK (read IN (0, 1)),
                  favorite INTEGER NOT NULL DEFAULT 0 CHECK (favorite IN (0, 1)),
                  read_later INTEGER NOT NULL DEFAULT 0 CHECK (read_later IN (0, 1))
                )
                """);
            statement.execute("INSERT INTO link_feed_items(id) VALUES ('" + itemId + "')");
            statement.execute("PRAGMA user_version = 1");
        }
    }

    private static long countRows(Path path, String table) throws SQLException {
        try (Connection connection = openSqlite(path);
            var statement = connection.createStatement();
            var result = statement.executeQuery("SELECT count(*) FROM " + table)) {
            return result.next() ? result.getLong(1) : -1;
        }
    }

    private static Connection openSqlite(Path path) throws SQLException {
        return new SQLiteConfig().createConnection("jdbc:sqlite:" + path);
    }

    private static long pragmaLong(Connection connection, String name)
        throws SQLException {
        try (var statement = connection.createStatement();
            var result = statement.executeQuery("PRAGMA " + name)) {
            return result.next() ? result.getLong(1) : -1;
        }
    }

    private static LinkFeedItem item(String id) {
        var item = new LinkFeedItem();
        item.setId(id);
        return item;
    }

    private static long pluginSqliteDriverCount() {
        ClassLoader pluginClassLoader = LinksSqliteDatabase.class.getClassLoader();
        return DriverManager.drivers()
            .filter(driver -> driver.getClass().getClassLoader() == pluginClassLoader)
            .filter(driver -> driver.getClass().getName().equals("org.sqlite.JDBC"))
            .count();
    }
}
