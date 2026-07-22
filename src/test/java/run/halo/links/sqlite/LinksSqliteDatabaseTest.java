package run.halo.links.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
            assertThat(database.isAvailable()).isTrue();
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
