## 1. SQLite Foundation

- [x] 1.1 Add Xerial SQLite JDBC to the Gradle runtime artifact while retaining the existing
  Nitrite libraries for the legacy migration reader.
- [x] 1.2 Add database lifecycle tests for schema creation, `user_version`, WAL mode,
  `synchronous=FULL`, busy timeout, explicit transaction rollback, and serialized access.
- [x] 1.3 Implement `LinksSqliteDatabase` with one guarded JDBC connection, versioned schema
  creation, indexes, transaction helpers, and clean shutdown.
- [x] 1.4 Introduce the minimal `LinkFeedStorageMaintenance` port and move maintenance callers away
  from the concrete Nitrite database type.

## 2. SQLite Feed Item Store

- [x] 2.1 Port the existing `LinkFeedItemStore` contract tests to a temporary SQLite database and
  cover all feed item fields and saved-state preservation.
- [x] 2.2 Implement SQLite upsert and point lookup behavior, preserving first-seen, read, favorite,
  and read-later state during refresh.
- [x] 2.3 Implement cursor listing and combined link, read, favorite, and read-later filters with
  the existing `(publishedAt DESC, id DESC)` ordering.
- [x] 2.4 Implement unread summaries, bulk mark-read, individual state updates, link-scoped delete,
  and count operations.
- [x] 2.5 Implement age, per-link, and global retention cleanup with favorite and read-later
  protection, plus threshold-based compaction.
- [x] 2.6 Run the complete store contract suite against SQLite and confirm behavior matches the
  Nitrite baseline.

## 3. Restart-Safe Nitrite Migration

- [x] 3.1 Create migration fixtures and tests covering all sixteen fields, multiple feed URLs,
  saved states, duplicate identities, and the maximum expected timestamp formats.
- [x] 3.2 Implement the read-only Nitrite migration reader and transactional insertion into a new
  temporary SQLite database.
- [x] 3.3 Add per-record decode failure handling that logs identifying information when available,
  skips the record, and continues migrating readable records.
- [x] 3.4 Validate schema version, readable and inserted counts, and `quick_check` before atomically
  promoting the temporary database.
- [x] 3.5 Implement the internal migration metadata and atomically written external completion
  marker, including recovery from a crash between SQLite promotion and marker creation.
- [x] 3.6 Add restart tests proving pre-cutover failures retry from unchanged Nitrite and completed
  migrations never reimport Nitrite after SQLite is removed or rebuilt.
- [x] 3.7 Handle a completely unreadable Nitrite database by isolating it, creating a validated
  empty SQLite database, and marking migration complete.
- [x] 3.8 Preserve a readable original Nitrite database at its existing path and verify no normal
  post-migration operation opens or modifies it.

## 4. Snapshots and Startup Recovery

- [x] 4.1 Implement SQLite-consistent snapshot creation through a temporary file, read-only
  `quick_check`, and atomic promotion without copying a live database or its sidecars.
- [x] 4.2 Create the initial snapshot immediately after migration and schedule subsequent snapshots
  after the daily interval has elapsed.
- [x] 4.3 Retain the newest two validated snapshots and verify that a failed new snapshot neither
  removes an older snapshot nor interrupts RSS operations.
- [x] 4.4 Validate the active database at startup and restore the newest snapshot that passes
  `quick_check` when the active database is unusable.
- [x] 4.5 When no snapshot is valid, isolate the active main/WAL/SHM file group, retain only the
  newest isolated group, and initialize an empty current-schema database without reimporting
  Nitrite.
- [x] 4.6 Add recovery tests for a damaged active database, damaged newest snapshot, all snapshots
  damaged, missing active database, stale sidecars, and repeated quarantine cleanup.

## 5. Runtime Integration and Failure Isolation

- [x] 5.1 Register SQLite as the production `LinkFeedItemStore`, remove active Nitrite lifecycle
  wiring, and delete Nitrite-only backup, recovery, and compaction code that is not required by the
  read-only migrator.
- [x] 5.2 Route scheduled maintenance, manual maintenance, and link-deletion cleanup through the
  store and maintenance ports while keeping blocking JDBC work on bounded-elastic execution.
- [x] 5.3 Implement bounded handling for transient busy/locked failures and transition RSS storage
  to unavailable after a persistent runtime database failure without replacing open files.
- [x] 5.4 Stop scheduled RSS work while unavailable, return service-unavailable errors from Console
  RSS operations, return empty theme-facing RSS results, and keep link and link-group management
  available.
- [x] 5.5 Verify Link deletion remains pending when its feed-cache cleanup cannot run and completes
  after storage becomes available following plugin restart.
- [x] 5.6 Verify skipped migration records and snapshot failures produce structured logs without a
  persistent Console warning, and verify no generated API client changes are required.

## 6. Verification and Platform Qualification

- [x] 6.1 Run backend tests and the full Gradle build, then check the worktree for unexpected
  generated or packaged changes.
- [x] 6.2 Exercise crash-window, rollback, disk-full/read-only, busy-lock, and plugin restart tests
  against temporary database directories.
- [x] 6.3 Benchmark the 100,000-item store for bulk migration, cursor listing, unread summaries,
  cleanup, snapshot creation, and startup `quick_check` to catch material regressions.
- [x] 6.4 Verify packaged native loading and database operations on Linux glibc and musl for x86_64
  and ARM64, plus macOS development, plugin restart, and hot reload.
- [x] 6.5 Verify native extraction behavior when the default temporary directory is mounted `noexec`
  and document the supported local-filesystem requirement and any required extraction setting.

## 7. Review Follow-up

- [x] 7.1 Treat a readable Nitrite database without the legacy RSS collection as an empty migration
  source, preserve it at the original path, and add a regression test.
- [x] 7.2 Validate closed migration and snapshot files through a truly read-only immutable SQLite
  connection without creating WAL or shared-memory sidecars.
- [x] 7.3 Add focused coverage for Link deletion completing after storage recovery and for duplicate
  source identities across feed URLs with supported timestamp precisions.
- [x] 7.4 Resolve the confirmed Java formatting issues, run focused tests and the full build, and
  validate the OpenSpec change strictly.

## 8. Final Review Follow-up

- [x] 8.1 Store SQLite timestamps in a fixed-width representation and cover mixed-precision
  ordering, cursor pagination, and retention cleanup.
- [x] 8.2 Require atomic file promotion for migration, markers, snapshots, and recovery instead of
  silently falling back to a non-atomic move.
- [x] 8.3 Base snapshot scheduling and rotation on snapshots that currently pass validation, and add
  a regression test for a damaged newest snapshot.
- [x] 8.4 Keep theme-facing SQLite reads on bounded-elastic execution and verify the execution
  thread without changing the public result contract.
- [x] 8.5 Record malformed legacy timestamp fields while retaining the otherwise readable item, and
  resolve the confirmed Mockito import issue.
- [x] 8.6 Run focused tests, the full build, strict OpenSpec validation, and final diff checks.

## 9. Cross-Plugin SQLite Follow-up

- [x] 9.1 Fall back from an unreadable or missing live Nitrite database to recognized JSON backups
  from newest to oldest while preserving the existing empty-cache behavior when all sources fail.
- [x] 9.2 Create migration and empty-cutover SQLite temporary files in rollback-journal mode and
  verify they have no WAL or shared-memory publication dependency.
- [x] 9.3 Manage the plugin-classloader SQLite JDBC registration lifecycle and verify repeated
  database destruction and recreation.
- [x] 9.4 Keep SQLite JDBC as a normal Gradle runtime dependency without duplicating the plugin
  packaging mechanism with an archive-entry verification task.
- [x] 9.5 Run focused tests, the full build, strict OpenSpec validation, and final diff checks.
