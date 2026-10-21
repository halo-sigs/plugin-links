package run.halo.links.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import run.halo.links.rss.LinkFeedItem;
import run.halo.links.rss.LinkFeedStorageUnavailableException;

class LinksSqliteLifecycleTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldLeaveCorruptDatabaseInPlaceWithOrWithoutMigrationMarker(boolean keepMarker)
        throws IOException {
        Path dbPath = tempDir.resolve("links.sqlite");
        Path snapshot = createOldSnapshot(dbPath);
        byte[] snapshotBytes = Files.readAllBytes(snapshot);
        if (!keepMarker) {
            Files.delete(tempDir.resolve("links.sqlite.migration-complete"));
        }
        Files.writeString(dbPath, "corrupted database");

        LinksSqliteDatabase database = new LinksSqliteDatabase(dbPath);
        try {
            assertThat(database.isAvailable()).isFalse();
            assertThatThrownBy(() -> new SqliteLinkFeedItemStore(database).count())
                .isInstanceOf(LinkFeedStorageUnavailableException.class);
            assertThat(Files.readString(dbPath)).isEqualTo("corrupted database");
            assertThat(Files.readAllBytes(snapshot)).isEqualTo(snapshotBytes);
            try (var files = Files.list(tempDir)) {
                assertThat(files.map(path -> path.getFileName().toString()))
                    .noneMatch(name -> name.startsWith("links.sqlite.corrupt-"));
            }
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldCreateEmptyDatabaseWithoutRestoringOldSnapshotWhenActiveFileIsMissing()
        throws IOException {
        Path dbPath = tempDir.resolve("links.sqlite");
        Path snapshot = createOldSnapshot(dbPath);
        byte[] snapshotBytes = Files.readAllBytes(snapshot);
        Files.delete(dbPath);

        LinksSqliteDatabase database = new LinksSqliteDatabase(dbPath);
        try {
            assertThat(database.isAvailable()).isTrue();
            assertThat(new SqliteLinkFeedItemStore(database).count()).isZero();
            assertThat(Files.readAllBytes(snapshot)).isEqualTo(snapshotBytes);
        } finally {
            database.destroy();
        }
    }

    @Test
    void shouldOpenDatabaseAfterUserRestoresIt() throws IOException {
        Path dbPath = tempDir.resolve("links.sqlite");
        Path snapshot = createOldSnapshot(dbPath);
        Files.writeString(dbPath, "corrupted database");
        LinksSqliteDatabase failed = new LinksSqliteDatabase(dbPath);
        try {
            assertThat(failed.isAvailable()).isFalse();
        } finally {
            failed.destroy();
        }

        Files.copy(snapshot, dbPath, StandardCopyOption.REPLACE_EXISTING);
        LinksSqliteDatabase restored = new LinksSqliteDatabase(dbPath);
        try {
            assertThat(restored.isAvailable()).isTrue();
            assertThat(new SqliteLinkFeedItemStore(restored).count()).isOne();
        } finally {
            restored.destroy();
        }
    }

    @Test
    void shouldPreserveFilesLeftByOldBackupAndRecoveryCode() throws IOException {
        Path snapshotTemp = tempDir.resolve("links.sqlite.snapshot-20260720000000000.tmp");
        Path restoreTemp = tempDir.resolve("links.sqlite.restore.tmp");
        Path quarantined = tempDir.resolve("links.sqlite.corrupt-20260720000000000");
        Files.writeString(snapshotTemp, "incomplete snapshot");
        Files.writeString(restoreTemp, "incomplete restore");
        Files.writeString(quarantined, "quarantined database");

        LinksSqliteDatabase database = new LinksSqliteDatabase(tempDir.resolve("links.sqlite"));
        try {
            assertThat(database.isAvailable()).isTrue();
            assertThat(Files.readString(snapshotTemp)).isEqualTo("incomplete snapshot");
            assertThat(Files.readString(restoreTemp)).isEqualTo("incomplete restore");
            assertThat(Files.readString(quarantined)).isEqualTo("quarantined database");
        } finally {
            database.destroy();
        }
    }

    private Path createOldSnapshot(Path dbPath) throws IOException {
        LinksSqliteDatabase database = new LinksSqliteDatabase(dbPath);
        try {
            LinkFeedItem item = new LinkFeedItem();
            item.setId("saved-item");
            new SqliteLinkFeedItemStore(database).upsert(item);
        } finally {
            database.destroy();
        }
        Path snapshot = tempDir.resolve("links.sqlite.snapshot-20260720000000000");
        return Files.copy(dbPath, snapshot);
    }
}
