package run.halo.links.nitrite;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.exceptions.NitriteIOException;
import org.dizitart.no2.mvstore.MVStoreModule;
import org.dizitart.no2.store.NitriteStore;
import org.dizitart.no2.support.exchange.ExportOptions;
import org.dizitart.no2.support.exchange.Exporter;
import org.dizitart.no2.support.exchange.ImportOptions;
import org.dizitart.no2.support.exchange.Importer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.PluginsRootGetter;

/**
 * Shared embedded database for feed cache records.
 */
@Slf4j
@Component
public class LinksNitriteDatabase implements DisposableBean {

    private static final DateTimeFormatter BACKUP_TS_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final int MAX_TIMESTAMPED_BACKUPS = 2;

    private Nitrite db;
    private final Path dbPath;

    @Autowired
    public LinksNitriteDatabase(PluginsRootGetter pluginsRootGetter) {
        this(resolveDbPath(pluginsRootGetter));
    }

    public LinksNitriteDatabase(Path dbPath) {
        this.dbPath = dbPath;
        try {
            Files.createDirectories(dbPath.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create feed database directory", e);
        }
        cleanupOrphanedTempFiles(dbPath);
        this.db = openWithRecovery(dbPath);
    }

    public synchronized <T> T withCollection(String name, CollectionCallback<T> callback) {
        return callback.apply(db.getCollection(name));
    }

    public synchronized void commit() {
        if (!db.isClosed() && db.hasUnsavedChanges()) {
            db.commit();
        }
    }

    public synchronized void compact() {
        if (db.isClosed()) {
            return;
        }
        try {
            NitriteStore<?> store = db.getStore();
            Field mvStoreField = store.getClass().getDeclaredField("mvStore");
            mvStoreField.setAccessible(true);
            org.h2.mvstore.MVStore mvStore = (org.h2.mvstore.MVStore) mvStoreField.get(store);
            mvStore.compactFile(10_000);
        } catch (Exception e) {
            log.warn("[plugin-links] Failed to compact feed database", e);
        }
    }

    @Scheduled(cron = "0 0 */12 * * *")
    public synchronized void scheduledBackup() {
        if (db.isClosed()) {
            return;
        }
        commit();
        sync(db);
        closeCurrentDatabase();
        try {
            createTimestampedJsonBackup(dbPath);
            rotateTimestampedBackups(dbPath, MAX_TIMESTAMPED_BACKUPS);
        } finally {
            reopenCurrentDatabase();
        }
    }

    @Override
    public synchronized void destroy() {
        if (db.isClosed()) {
            return;
        }
        commit();
        sync(db);
        closeCurrentDatabase();
        createTimestampedJsonBackup(dbPath);
        rotateTimestampedBackups(dbPath, MAX_TIMESTAMPED_BACKUPS);
    }

    private void closeCurrentDatabase() {
        if (!db.isClosed()) {
            db.close();
        }
    }

    private void reopenCurrentDatabase() {
        if (db.isClosed()) {
            db = openWithRecovery(dbPath);
        }
    }

    private static Nitrite openWithRecovery(Path dbPath) {
        if (!Files.exists(dbPath)) {
            restoreFirstAvailable(dbPath);
        }
        try {
            return openDatabase(dbPath);
        } catch (NitriteIOException e) {
            log.warn("[plugin-links] Feed database at {} is corrupted, attempting recovery",
                dbPath, e);
            return recoverDatabase(dbPath);
        }
    }

    private static Nitrite recoverDatabase(Path dbPath) {
        archiveCorrupted(dbPath);
        for (Path candidate : timestampedBackups(dbPath)) {
            try {
                restoreCandidate(candidate, dbPath);
                return openDatabase(dbPath);
            } catch (Exception e) {
                log.warn("[plugin-links] Restore from {} failed, trying next backup", candidate,
                    e);
                try {
                    Files.deleteIfExists(dbPath);
                } catch (IOException ignored) {
                    // best-effort cleanup before next attempt
                }
            }
        }
        log.warn("[plugin-links] No valid feed database backup found. Starting with an empty feed "
            + "cache.");
        return openDatabase(dbPath);
    }

    private static void restoreFirstAvailable(Path dbPath) {
        for (Path candidate : timestampedBackups(dbPath)) {
            try {
                restoreCandidate(candidate, dbPath);
                return;
            } catch (Exception e) {
                log.warn("[plugin-links] Pre-open restore from {} failed, trying next backup",
                    candidate, e);
                try {
                    Files.deleteIfExists(dbPath);
                } catch (IOException ignored) {
                    // best-effort cleanup before next attempt
                }
            }
        }
    }

    private static List<Path> timestampedBackups(Path dbPath) {
        Path dir = dbPath.getParent();
        String prefix = dbPath.getFileName() + ".bak-";
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().startsWith(prefix))
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .sorted(Comparator.comparing((Path path) -> path.getFileName().toString())
                    .reversed())
                .collect(Collectors.toList());
        } catch (IOException e) {
            log.warn("[plugin-links] Could not list feed database backups in {}", dir, e);
            return List.of();
        }
    }

    private static void createTimestampedJsonBackup(Path dbPath) {
        if (!Files.exists(dbPath)) {
            return;
        }
        String timestamp = LocalDateTime.now().format(BACKUP_TS_FORMAT);
        Path target = dbPath.resolveSibling(dbPath.getFileName() + ".bak-" + timestamp + ".json");
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            ExportOptions exportOptions = new ExportOptions();
            exportOptions.setNitriteFactory(() -> openDatabase(dbPath));
            Exporter.withOptions(exportOptions).exportTo(temp.toFile());
            moveReplacing(temp, target);
        } catch (Exception e) {
            log.warn("[plugin-links] Failed to create feed database backup at {}", target, e);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // best-effort cleanup
            }
        }
    }

    private static void rotateTimestampedBackups(Path dbPath, int maxCount) {
        Path dir = dbPath.getParent();
        String prefix = dbPath.getFileName() + ".bak-";
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> backups = stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().startsWith(prefix))
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .collect(Collectors.toList());
            int toDelete = backups.size() - maxCount;
            for (int i = 0; i < toDelete; i++) {
                Files.deleteIfExists(backups.get(i));
            }
        } catch (IOException e) {
            log.warn("[plugin-links] Failed to rotate feed database backups in {}", dir, e);
        }
    }

    private static void restoreCandidate(Path candidate, Path dbPath) throws IOException {
        Files.deleteIfExists(dbPath);
        ImportOptions importOptions = new ImportOptions();
        importOptions.setNitriteFactory(() -> openDatabase(dbPath));
        Importer.withOptions(importOptions).importFrom(candidate.toFile());
    }

    private static void archiveCorrupted(Path dbPath) {
        if (!Files.exists(dbPath)) {
            return;
        }
        try {
            Files.move(dbPath, dbPath.resolveSibling(
                dbPath.getFileName() + ".corrupted." + UUID.randomUUID()));
        } catch (IOException e) {
            log.warn("[plugin-links] Could not archive corrupted feed database at {}", dbPath, e);
            try {
                Files.deleteIfExists(dbPath);
            } catch (IOException ignored) {
                // best-effort cleanup
            }
        }
    }

    private static void cleanupOrphanedTempFiles(Path dbPath) {
        Path dir = dbPath.getParent();
        String baseName = dbPath.getFileName().toString();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString();
                    return name.startsWith(baseName + ".bak-") && name.endsWith(".json.tmp");
                })
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        log.warn("[plugin-links] Failed to delete orphaned feed database temp "
                            + "file {}", path, e);
                    }
                });
        } catch (IOException e) {
            log.warn("[plugin-links] Failed to scan feed database temp files in {}", dir, e);
        }
    }

    private static void sync(Nitrite database) {
        try {
            NitriteStore<?> store = database.getStore();
            Field mvStoreField = store.getClass().getDeclaredField("mvStore");
            mvStoreField.setAccessible(true);
            org.h2.mvstore.MVStore mvStore = (org.h2.mvstore.MVStore) mvStoreField.get(store);
            mvStore.sync();
        } catch (Exception e) {
            log.warn("[plugin-links] Failed to sync feed database", e);
        }
    }

    private static Nitrite openDatabase(Path dbPath) {
        return Nitrite.builder()
            .loadModule(MVStoreModule.withConfig()
                .filePath(dbPath.toFile())
                .compress(true)
                .build())
            .openOrCreate();
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path resolveDbPath(PluginsRootGetter pluginsRootGetter) {
        Path pluginDataDir = pluginsRootGetter.get().resolve("links");
        return pluginDataDir.resolve("links.nitrite");
    }

    @FunctionalInterface
    public interface CollectionCallback<T> {
        T apply(NitriteCollection collection);
    }
}
