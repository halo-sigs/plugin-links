package run.halo.links.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConnection;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;
import org.sqlite.JDBC;
import run.halo.app.plugin.PluginsRootGetter;
import run.halo.links.rss.LinkFeedStorageMaintenance;
import run.halo.links.rss.LinkFeedStorageUnavailableException;

@Slf4j
@Component
public class LinksSqliteDatabase implements DisposableBean, LinkFeedStorageMaintenance {

    static final int SCHEMA_VERSION = 1;
    static final String MIGRATION_METADATA_KEY = "nitrite_migration_complete";
    private static final int BUSY_TIMEOUT_MILLIS = 5_000;
    private static final int MAX_BUSY_RETRIES = 2;
    private static final int MAX_SNAPSHOTS = 2;
    private static final double VACUUM_FREE_PAGE_RATIO = 0.25d;
    private static final Duration SNAPSHOT_INTERVAL = Duration.ofDays(1);
    private static final DateTimeFormatter FILE_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneOffset.UTC);
    private static final Set<SQLiteErrorCode> UNAVAILABLE_CODES = Set.of(
        SQLiteErrorCode.SQLITE_CORRUPT,
        SQLiteErrorCode.SQLITE_NOTADB,
        SQLiteErrorCode.SQLITE_IOERR,
        SQLiteErrorCode.SQLITE_FULL,
        SQLiteErrorCode.SQLITE_CANTOPEN,
        SQLiteErrorCode.SQLITE_PROTOCOL,
        SQLiteErrorCode.SQLITE_READONLY
    );
    private static int liveDatabaseComponents;

    private final Path dbPath;
    private final Path legacyDbPath;
    private final Path migrationMarkerPath;
    private final Clock clock;
    private Connection connection;
    private volatile boolean available;
    private boolean driverLeaseHeld;

    @Autowired
    public LinksSqliteDatabase(PluginsRootGetter pluginsRootGetter) {
        this(resolveDbPath(pluginsRootGetter), Clock.systemUTC());
    }

    public LinksSqliteDatabase(Path dbPath) {
        this(dbPath, Clock.systemUTC());
    }

    LinksSqliteDatabase(Path dbPath, Clock clock) {
        this.dbPath = dbPath;
        this.legacyDbPath = dbPath.resolveSibling("links.nitrite");
        this.migrationMarkerPath = dbPath.resolveSibling(
            dbPath.getFileName() + ".migration-complete");
        this.clock = clock;
        try {
            acquireDriverLease();
            driverLeaseHeld = true;
            Files.createDirectories(dbPath.getParent());
            cleanupTemporaryFiles();
            boolean migrated = prepareMigration();
            restoreOrCreateActiveDatabase();
            this.connection = openActiveDatabase(dbPath);
            this.available = true;
            if (migrated) {
                createSnapshot();
            }
        } catch (Exception e) {
            closeQuietly(connection);
            connection = null;
            available = false;
            releaseDriverLease();
            log.error("[plugin-links] RSS SQLite storage could not start at {}; link management "
                + "will remain available", dbPath, e);
        }
    }

    public synchronized <T> T execute(SqlCallback<T> callback) {
        requireAvailable();
        int busyRetries = 0;
        while (true) {
            try {
                return callback.apply(connection);
            } catch (SQLException e) {
                if (isBusy(e) && busyRetries++ < MAX_BUSY_RETRIES) {
                    sleepBeforeRetry();
                    continue;
                }
                if (isStorageFailure(e) || isBusy(e)) {
                    available = false;
                    log.error("[plugin-links] RSS SQLite storage became unavailable at {}",
                        dbPath, e);
                    throw new LinkFeedStorageUnavailableException(
                        "RSS feed storage is unavailable until the plugin restarts.", e);
                }
                throw new IllegalStateException("Feed database operation failed", e);
            }
        }
    }

    public synchronized <T> T inTransaction(SqlCallback<T> callback) {
        return execute(current -> {
            boolean autoCommit = current.getAutoCommit();
            current.setAutoCommit(false);
            try {
                T result = callback.apply(current);
                current.commit();
                current.setAutoCommit(autoCommit);
                return result;
            } catch (SQLException | RuntimeException e) {
                try {
                    current.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                try {
                    current.setAutoCommit(autoCommit);
                } catch (SQLException autoCommitError) {
                    e.addSuppressed(autoCommitError);
                }
                throw e;
            }
        });
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public synchronized void compactIfNeeded() {
        if (!available) {
            return;
        }
        try {
            execute(current -> {
                long pageCount = pragmaLong(current, "page_count");
                long freePages = pragmaLong(current, "freelist_count");
                if (pageCount > 0 && (double) freePages / pageCount >= VACUUM_FREE_PAGE_RATIO) {
                    try (Statement statement = current.createStatement()) {
                        statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                        statement.execute("VACUUM");
                    }
                }
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("[plugin-links] Failed to compact RSS SQLite database", e);
        }
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000L, initialDelay = 60 * 60 * 1000L)
    public synchronized void snapshotIfDue() {
        if (!available || !snapshotIsDue()) {
            return;
        }
        createSnapshot();
    }

    synchronized void createSnapshot() {
        if (!available || isClosed() || !Files.exists(dbPath)) {
            return;
        }
        String timestamp = FILE_TIMESTAMP.format(Instant.now(clock));
        Path target = dbPath.resolveSibling(dbPath.getFileName() + ".snapshot-" + timestamp);
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.deleteIfExists(temp);
            SQLiteConnection sqliteConnection = connection.unwrap(SQLiteConnection.class);
            int result = sqliteConnection.getDatabase().backup("main", temp.toString(), null);
            if (result != SQLiteErrorCode.SQLITE_OK.code) {
                throw new SQLException("SQLite backup failed with result code " + result);
            }
            if (!isValidStandaloneDatabase(temp)) {
                throw new SQLException("New SQLite snapshot failed validation: " + temp);
            }
            deleteQuietly(sidecar(temp, "-wal"));
            deleteQuietly(sidecar(temp, "-shm"));
            moveReplacing(temp, target);
            Files.setLastModifiedTime(target, FileTime.from(Instant.now(clock)));
            rotateSnapshots();
        } catch (Exception e) {
            log.warn("[plugin-links] Failed to create RSS SQLite snapshot at {}", target, e);
            deleteQuietly(temp);
            deleteQuietly(sidecar(temp, "-wal"));
            deleteQuietly(sidecar(temp, "-shm"));
        }
    }

    @Override
    public synchronized void destroy() {
        available = false;
        closeQuietly(connection);
        connection = null;
        releaseDriverLease();
    }

    Path dbPath() {
        return dbPath;
    }

    Path migrationMarkerPath() {
        return migrationMarkerPath;
    }

    static Connection openActiveDatabase(Path path) throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.FULL);
        config.setBusyTimeout(BUSY_TIMEOUT_MILLIS);
        Connection result = config.createConnection("jdbc:sqlite:" + path);
        try {
            createSchema(result);
            return result;
        } catch (SQLException e) {
            closeQuietly(result);
            throw e;
        }
    }

    static Connection openMigrationDatabase(Path path) throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setJournalMode(SQLiteConfig.JournalMode.DELETE);
        config.setSynchronous(SQLiteConfig.SynchronousMode.FULL);
        config.setBusyTimeout(BUSY_TIMEOUT_MILLIS);
        Connection result = config.createConnection("jdbc:sqlite:" + path);
        try {
            createSchema(result);
            try (Statement statement = result.createStatement();
                ResultSet journalMode = statement.executeQuery("PRAGMA journal_mode")) {
                if (!journalMode.next()
                    || !"delete".equalsIgnoreCase(journalMode.getString(1))) {
                    throw new SQLException("SQLite migration database did not enter DELETE "
                        + "journal mode");
                }
            }
            return result;
        } catch (SQLException e) {
            closeQuietly(result);
            throw e;
        }
    }

    static void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS link_feed_items (
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
                  read_later INTEGER NOT NULL DEFAULT 0 CHECK (read_later IN (0, 1)),
                  hidden INTEGER NOT NULL DEFAULT 0 CHECK (hidden IN (0, 1))
                )
                """);
            ensureHiddenColumn(connection);
            statement.execute("""
                CREATE TABLE IF NOT EXISTS storage_metadata (
                  key TEXT PRIMARY KEY,
                  value TEXT NOT NULL
                )
                """);
            statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_feed_items_recent
                ON link_feed_items(published_at DESC, id DESC)
                """);
            statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_feed_items_link_recent
                ON link_feed_items(link_name, published_at DESC, id DESC)
                """);
            statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_feed_items_feed
                ON link_feed_items(link_name, feed_url)
                """);
            statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_feed_items_first_seen
                ON link_feed_items(first_seen_at)
                """);
            statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_feed_items_states
                ON link_feed_items(read, favorite, read_later, link_name)
                """);
            statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_feed_items_hidden_recent
                ON link_feed_items(hidden, published_at DESC, id DESC)
                """);
            statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
        }
    }

    private static void ensureHiddenColumn(Connection connection) throws SQLException {
        boolean hiddenColumnExists = false;
        try (Statement statement = connection.createStatement();
            ResultSet columns = statement.executeQuery("PRAGMA table_info(link_feed_items)")) {
            while (columns.next()) {
                if ("hidden".equalsIgnoreCase(columns.getString("name"))) {
                    hiddenColumnExists = true;
                    break;
                }
            }
        }
        if (!hiddenColumnExists) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                    ALTER TABLE link_feed_items
                    ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0 CHECK (hidden IN (0, 1))
                    """);
            }
        }
    }

    static boolean isValidDatabase(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        SQLiteConfig config = new SQLiteConfig();
        config.setBusyTimeout(BUSY_TIMEOUT_MILLIS);
        try (Connection candidate = config.createConnection("jdbc:sqlite:" + path);
            Statement statement = candidate.createStatement()) {
            statement.execute("PRAGMA query_only = ON");
            return hasCurrentSchemaAndPassesQuickCheck(statement);
        } catch (SQLException e) {
            return false;
        }
    }

    static boolean isValidStandaloneDatabase(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        try (Connection candidate = openStandaloneReadOnly(path);
            Statement statement = candidate.createStatement()) {
            return hasCurrentSchemaAndPassesQuickCheck(statement);
        } catch (SQLException e) {
            return false;
        }
    }

    static Connection openStandaloneReadOnly(Path path) throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.setBusyTimeout(BUSY_TIMEOUT_MILLIS);
        String fileUri = path.toAbsolutePath().normalize().toUri().toASCIIString();
        return config.createConnection("jdbc:sqlite:" + fileUri + "?mode=ro&immutable=1");
    }

    static void markMigrationComplete(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("""
            INSERT INTO storage_metadata(key, value) VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
            """)) {
            statement.setString(1, MIGRATION_METADATA_KEY);
            statement.setString(2, "true");
            statement.executeUpdate();
        }
    }

    private boolean prepareMigration() throws IOException, SQLException {
        if (Files.exists(migrationMarkerPath)) {
            return false;
        }
        if (hasInternalMigrationMarker(dbPath) && isValidDatabase(dbPath)) {
            writeMigrationMarker();
            return false;
        }
        if (NitriteToSqliteMigration.hasLegacySource(legacyDbPath)) {
            Path temp = dbPath.resolveSibling(dbPath.getFileName() + ".migration.tmp");
            NitriteToSqliteMigration.Result result =
                NitriteToSqliteMigration.migrate(legacyDbPath, temp, clock);
            boolean isolateLegacy = false;
            if (result == NitriteToSqliteMigration.Result.SOURCE_UNREADABLE) {
                isolateLegacy = Files.exists(legacyDbPath);
                createEmptyMigrationDatabase(temp);
            } else if (result == NitriteToSqliteMigration.Result.MIGRATED_FROM_BACKUP
                && Files.exists(legacyDbPath)) {
                isolateLegacy = true;
            }
            moveReplacing(temp, dbPath);
            if (isolateLegacy) {
                isolateLegacyDatabase();
            }
            writeMigrationMarker();
            return true;
        }
        if (!Files.exists(dbPath) || !isValidDatabase(dbPath)) {
            restoreOrCreateActiveDatabase();
        }
        try (Connection candidate = openActiveDatabase(dbPath)) {
            markMigrationComplete(candidate);
        }
        writeMigrationMarker();
        return false;
    }

    private void createEmptyMigrationDatabase(Path temp) throws IOException, SQLException {
        Files.deleteIfExists(temp);
        try (Connection candidate = openMigrationDatabase(temp)) {
            markMigrationComplete(candidate);
        }
        if (!isValidStandaloneDatabase(temp)) {
            throw new SQLException("Empty migration database failed validation: " + temp);
        }
    }

    private void restoreOrCreateActiveDatabase() throws IOException, SQLException {
        if (isValidDatabase(dbPath)) {
            return;
        }
        if (Files.exists(dbPath) || Files.exists(sidecar(dbPath, "-wal"))
            || Files.exists(sidecar(dbPath, "-shm"))) {
            isolateActiveDatabase();
        }
        for (Path snapshot : snapshots()) {
            if (!isValidStandaloneDatabase(snapshot)) {
                continue;
            }
            Path temp = dbPath.resolveSibling(dbPath.getFileName() + ".restore.tmp");
            Files.copy(snapshot, temp, StandardCopyOption.REPLACE_EXISTING);
            if (!isValidStandaloneDatabase(temp)) {
                deleteQuietly(temp);
                continue;
            }
            moveReplacing(temp, dbPath);
            return;
        }
        try (Connection ignored = openActiveDatabase(dbPath)) {
            // Schema creation initializes an empty database.
        }
    }

    private boolean hasInternalMigrationMarker(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        try (Connection candidate = config.createConnection("jdbc:sqlite:" + path);
            var statement = candidate.prepareStatement(
                "SELECT value FROM storage_metadata WHERE key = ?")) {
            statement.setString(1, MIGRATION_METADATA_KEY);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && "true".equals(result.getString(1));
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private void writeMigrationMarker() throws IOException {
        Path temp = migrationMarkerPath.resolveSibling(migrationMarkerPath.getFileName() + ".tmp");
        Files.writeString(temp, "completed\n");
        moveReplacing(temp, migrationMarkerPath);
    }

    private void isolateLegacyDatabase() throws IOException {
        Path target = legacyDbPath.resolveSibling(
            legacyDbPath.getFileName() + ".corrupt-" + FILE_TIMESTAMP.format(Instant.now(clock)));
        Files.move(legacyDbPath, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private void isolateActiveDatabase() throws IOException {
        String suffix = ".corrupt-" + FILE_TIMESTAMP.format(Instant.now(clock)) + "-"
            + UUID.randomUUID();
        moveIfExists(dbPath, dbPath.resolveSibling(dbPath.getFileName() + suffix));
        moveIfExists(sidecar(dbPath, "-wal"),
            dbPath.resolveSibling(dbPath.getFileName() + suffix + "-wal"));
        moveIfExists(sidecar(dbPath, "-shm"),
            dbPath.resolveSibling(dbPath.getFileName() + suffix + "-shm"));
        rotateQuarantinedGroups();
    }

    private void rotateQuarantinedGroups() throws IOException {
        String prefix = dbPath.getFileName() + ".corrupt-";
        try (Stream<Path> stream = Files.list(dbPath.getParent())) {
            List<Path> mainFiles = stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().startsWith(prefix))
                .filter(path -> !path.getFileName().toString().endsWith("-wal"))
                .filter(path -> !path.getFileName().toString().endsWith("-shm"))
                .sorted(Comparator.comparing((Path path) -> path.getFileName().toString())
                    .reversed())
                .toList();
            for (int i = 1; i < mainFiles.size(); i++) {
                Path old = mainFiles.get(i);
                deleteQuietly(old);
                deleteQuietly(old.resolveSibling(old.getFileName() + "-wal"));
                deleteQuietly(old.resolveSibling(old.getFileName() + "-shm"));
            }
        }
    }

    private boolean snapshotIsDue() {
        List<Path> snapshots = validSnapshots();
        if (snapshots.isEmpty()) {
            return true;
        }
        try {
            Instant latest = Files.getLastModifiedTime(snapshots.get(0)).toInstant();
            return !Instant.now(clock).isBefore(latest.plus(SNAPSHOT_INTERVAL));
        } catch (IOException e) {
            return true;
        }
    }

    private List<Path> snapshots() {
        String prefix = dbPath.getFileName() + ".snapshot-";
        try (Stream<Path> stream = Files.list(dbPath.getParent())) {
            return stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().startsWith(prefix))
                .filter(path -> !path.getFileName().toString().endsWith(".tmp"))
                .filter(path -> !path.getFileName().toString().endsWith("-wal"))
                .filter(path -> !path.getFileName().toString().endsWith("-shm"))
                .sorted(Comparator.comparing((Path path) -> path.getFileName().toString())
                    .reversed())
                .toList();
        } catch (IOException e) {
            log.warn("[plugin-links] Failed to list RSS SQLite snapshots", e);
            return List.of();
        }
    }

    private void rotateSnapshots() {
        List<Path> snapshots = snapshots();
        List<Path> validSnapshots = snapshots.stream()
            .filter(LinksSqliteDatabase::isValidStandaloneDatabase)
            .toList();
        snapshots.stream()
            .filter(snapshot -> !validSnapshots.contains(snapshot))
            .forEach(LinksSqliteDatabase::deleteQuietly);
        for (int i = MAX_SNAPSHOTS; i < validSnapshots.size(); i++) {
            deleteQuietly(validSnapshots.get(i));
        }
    }

    private List<Path> validSnapshots() {
        return snapshots().stream()
            .filter(LinksSqliteDatabase::isValidStandaloneDatabase)
            .toList();
    }

    private void cleanupTemporaryFiles() throws IOException {
        String dbName = dbPath.getFileName().toString();
        String legacyImportName = legacyDbPath.getFileName() + ".migration-import.tmp";
        try (Stream<Path> stream = Files.list(dbPath.getParent())) {
            stream.filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString();
                    return name.equals(legacyImportName)
                        || name.startsWith(dbName) && (name.endsWith(".tmp")
                            || name.contains(".tmp-"));
                })
                .forEach(LinksSqliteDatabase::deleteQuietly);
        }
    }

    private void requireAvailable() {
        if (!available || isClosed()) {
            available = false;
            throw new LinkFeedStorageUnavailableException(
                "RSS feed storage is unavailable until the plugin restarts.");
        }
    }

    private boolean isClosed() {
        try {
            return connection == null || connection.isClosed();
        } catch (SQLException e) {
            return true;
        }
    }

    private static long pragmaLong(Connection connection, String pragma) throws SQLException {
        try (Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("PRAGMA " + pragma)) {
            return result.next() ? result.getLong(1) : 0;
        }
    }

    private static boolean hasCurrentSchemaAndPassesQuickCheck(Statement statement)
        throws SQLException {
        try (ResultSet version = statement.executeQuery("PRAGMA user_version")) {
            if (!version.next() || version.getInt(1) != SCHEMA_VERSION) {
                return false;
            }
        }
        try (ResultSet check = statement.executeQuery("PRAGMA quick_check")) {
            return check.next() && "ok".equalsIgnoreCase(check.getString(1)) && !check.next();
        }
    }

    private static boolean isBusy(SQLException error) {
        return error instanceof SQLiteException sqliteError
            && (sqliteError.getResultCode() == SQLiteErrorCode.SQLITE_BUSY
            || sqliteError.getResultCode() == SQLiteErrorCode.SQLITE_LOCKED
            || sqliteError.getResultCode() == SQLiteErrorCode.SQLITE_BUSY_TIMEOUT
            || sqliteError.getResultCode() == SQLiteErrorCode.SQLITE_BUSY_RECOVERY
            || sqliteError.getResultCode() == SQLiteErrorCode.SQLITE_BUSY_SNAPSHOT);
    }

    private static boolean isStorageFailure(SQLException error) {
        if (!(error instanceof SQLiteException sqliteError)) {
            return false;
        }
        SQLiteErrorCode code = sqliteError.getResultCode();
        return UNAVAILABLE_CODES.contains(code)
            || code.name().startsWith("SQLITE_IOERR_")
            || code.name().startsWith("SQLITE_CORRUPT_")
            || code.name().startsWith("SQLITE_CANTOPEN_")
            || code.name().startsWith("SQLITE_READONLY_");
    }

    private static void sleepBeforeRetry() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void moveIfExists(Path source, Path target) throws IOException {
        if (Files.exists(source)) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path sidecar(Path path, String suffix) {
        return path.resolveSibling(path.getFileName() + suffix);
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("[plugin-links] Failed to delete RSS SQLite file {}", path, e);
        }
    }

    private static void closeQuietly(Connection candidate) {
        if (candidate == null) {
            return;
        }
        try {
            candidate.close();
        } catch (SQLException e) {
            log.warn("[plugin-links] Failed to close RSS SQLite connection", e);
        }
    }

    private static synchronized void acquireDriverLease() throws SQLException {
        ensureSqliteDriverRegistered();
        liveDatabaseComponents++;
    }

    private void releaseDriverLease() {
        if (!driverLeaseHeld) {
            return;
        }
        driverLeaseHeld = false;
        releaseSqliteDriver();
    }

    private static synchronized void releaseSqliteDriver() {
        if (liveDatabaseComponents == 0) {
            return;
        }
        liveDatabaseComponents--;
        if (liveDatabaseComponents == 0) {
            deregisterPluginSqliteDrivers();
        }
    }

    private static void ensureSqliteDriverRegistered() throws SQLException {
        try {
            DriverManager.getDriver("jdbc:sqlite::memory:");
            return;
        } catch (SQLException ignored) {
            // Register below when service loading or a previous plugin lifecycle left none.
        }
        JDBC candidate = new JDBC();
        try {
            DriverManager.getDriver("jdbc:sqlite::memory:");
        } catch (SQLException ignored) {
            DriverManager.registerDriver(candidate);
        }
    }

    private static void deregisterPluginSqliteDrivers() {
        ClassLoader pluginClassLoader = LinksSqliteDatabase.class.getClassLoader();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (!JDBC.class.getName().equals(driver.getClass().getName())
                || driver.getClass().getClassLoader() != pluginClassLoader) {
                continue;
            }
            try {
                DriverManager.deregisterDriver(driver);
            } catch (SQLException e) {
                log.warn("[plugin-links] Failed to deregister SQLite JDBC driver", e);
            }
        }
    }

    private static Path resolveDbPath(PluginsRootGetter pluginsRootGetter) {
        return pluginsRootGetter.get().resolve("links").resolve("links.sqlite");
    }

    @FunctionalInterface
    public interface SqlCallback<T> {
        T apply(Connection connection) throws SQLException;
    }
}
