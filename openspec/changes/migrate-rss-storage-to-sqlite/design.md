## Context

RSS feed items are currently accessed through `LinkFeedItemStore` and implemented by
`NitriteLinkFeedItemStore`. `LinksNitriteDatabase` additionally owns file opening, periodic JSON
backup, recovery, compaction, and shutdown handling. Console endpoints and the RSS scheduler call
the store from Reactor bounded-elastic execution, but some maintenance paths depend directly on
the Nitrite database class.

The store contains up to 100,000 records and persists sixteen data fields, including the user's
read, favorite, and read-later flags. The agreed product policy nevertheless treats the entire RSS
store as a reconstructable local cache: migration should retain as much legacy data as possible,
but an unrecoverable database must not prevent the links plugin from starting.

SQLite is embedded and synchronous, matching the current store boundary. It improves transaction,
index, consistency-check, and backup support without introducing a server database or a reactive
database stack. SQLite is still subject to filesystem, hardware, and unsafe file-operation
failures, so this design uses two daily consistent snapshots rather than claiming corruption is
impossible.

## Goals / Non-Goals

**Goals:**

- Make SQLite the only active RSS feed item store while preserving existing store semantics.
- Complete a one-time, restart-safe migration from the existing Nitrite database.
- Keep the original Nitrite source intact whenever it can be opened.
- Recover automatically at startup from the newest valid daily snapshot.
- Rebuild an empty RSS cache when neither the active database nor snapshots are usable.
- Confine runtime storage failures to RSS functionality.
- Keep the design small enough to be covered by store-contract and lifecycle tests.

**Non-Goals:**

- Guarantee that RSS cache data or saved item states can never be lost.
- Dual-write Nitrite and SQLite after cutover.
- Reimport Nitrite after migration has been marked complete.
- Support SQLite databases on NFS or other filesystems without reliable local locking.
- Introduce R2DBC, a connection pool, a database server, or a user-facing backup manager.
- Change RSS API payloads, feed parsing, retention limits, or Console reading workflows.
- Remove the Nitrite libraries in the migration release; direct upgrades still need a reader.

## Decisions

### 1. Preserve the store port and use one serialized JDBC connection

`SqliteLinkFeedItemStore` will implement the existing `LinkFeedItemStore` contract. A
`LinksSqliteDatabase` lifecycle component will own one `java.sql.Connection`, schema creation,
startup recovery, transactions, and shutdown. Every connection operation is guarded by one lock.
Callers continue moving blocking store work to Reactor's bounded-elastic scheduler.

The component also owns the plugin-classloader JDBC registration lifecycle. Construction ensures
an SQLite driver is registered, and destruction of the last live database component deregisters
only SQLite drivers loaded by this plugin classloader. This prevents a retired plugin classloader
from remaining reachable across disable, enable, and hot-reload cycles while allowing a later
component lifecycle to register the driver again.

This is preferred to R2DBC or a connection pool because existing store calls are synchronous,
the current Nitrite store is globally serialized, and the expected database is small. It also
reduces WAL checkpoint and file-replacement races.

Maintenance callers will depend on a small `LinkFeedStorageMaintenance` interface rather than a
concrete database class. The interface exposes only lifecycle operations actually needed by the
scheduler or endpoints, such as conditional compaction and snapshot triggering.

### 2. Use an explicitly versioned SQLite schema

The database uses `PRAGMA user_version` for schema versioning. Feed item timestamps are ISO-8601
TEXT values, boolean states are constrained integer values, and the stable item ID is the primary
key. Indexes will cover the stable cursor order `(published_at DESC, id DESC)`, link-scoped cursor
queries, saved/read-state filters, and cleanup queries.

Upserts update refreshed content and timestamps while preserving `first_seen_at`, `read`,
`favorite`, and `read_later` for existing rows. Multi-row writes, bulk state changes, and cleanup
execute in explicit transactions.

Each opened active database is configured with foreign-key checking where applicable,
`journal_mode=WAL`, `synchronous=FULL`, and a bounded busy timeout. `BUSY` and `LOCKED` receive only
bounded transient handling; generic `SQLException` values are not automatically classified as
corruption.

### 3. Treat migration as a restart-safe state machine

The migration uses three durable signals:

- the untouched legacy `links.nitrite` source;
- an internal metadata row written in the temporary SQLite transaction; and
- an external migration-complete marker that remains when SQLite is later reset.

When no external marker exists, startup proceeds as follows:

1. If a final SQLite database has the internal completed marker and passes validation, recreate
   the external marker. This closes the crash window between database promotion and marker write.
2. Otherwise try the live Nitrite database, then recognized timestamped Nitrite JSON backups from
   newest to oldest, and create a new temporary SQLite database from the first readable source.
3. Read legacy documents one at a time, map all sixteen fields, and insert them in one transaction.
   An unreadable document is logged and skipped; other documents continue.
4. Write the internal completion metadata, commit, validate schema version, source/readable/inserted
   counts, and `quick_check`, then close the temporary database.
5. Atomically move the temporary file to `links.sqlite`, then atomically create the external
   completion marker.
6. Create the initial validated SQLite snapshot.

The temporary migration database uses rollback-journal mode instead of WAL so the validated
cutover artifact is one self-contained file. Any failure before promotion leaves readable Nitrite
sources untouched and causes a full retry on the next plugin start. A stale temporary database is
isolated or replaced, never resumed. If the live Nitrite database is unreadable, it is isolated
after a JSON backup succeeds. If no live database or recognized backup can be read, the unreadable
live file is isolated, an empty validated SQLite database is promoted, and migration is marked
complete.

After the external marker exists, startup never imports Nitrite again, even when `links.sqlite` is
missing. The original readable Nitrite database stays at its existing path as a permanent,
read-only migration-time snapshot. Consequently, downgrading to the old plugin can only see data
as it existed at cutover.

Nitrite, MVStore, and object-mapping dependencies remain packaged for the read-only migrator.
Removing them requires a separate future decision about how long direct upgrades from Nitrite
versions remain supported.

### 4. Use SQLite-consistent snapshots, never live file copies

The database component creates a snapshot with SQLite's backup mechanism while access is
serialized. It writes a new temporary snapshot, closes it, opens it read-only, runs `quick_check`,
and atomically promotes it into the snapshot set. The plugin creates one immediately after a
successful migration and then one when a daily interval has elapsed.

Only after a new snapshot validates does rotation remove snapshots older than the newest two.
Snapshot failure leaves the active database and previous snapshots unchanged, records a structured
log, and does not make RSS unavailable or create a persistent Console warning. Directly copying
`links.sqlite`, `links.sqlite-wal`, or `links.sqlite-shm` is prohibited.

Daily snapshots are distinct from database compaction. A full `VACUUM` does not run daily; it runs
only after cleanup and only when the free-page ratio exceeds a chosen implementation threshold.
Manual maintenance may reuse the same guarded operation.

### 5. Recover only during startup

Startup runs `quick_check` before exposing the store. If the active database fails to open or
validate, the component closes it, isolates its main/WAL/SHM file group, and examines snapshots
from newest to oldest. A snapshot must pass `quick_check` before being restored through a temporary
file and atomic move.

If no snapshot validates, the component creates a new empty database at the current schema version.
The external migration marker prevents the retained Nitrite source from being imported again.
Only the most recent isolated failed database file group is retained; the validated snapshots have
their own two-file retention policy.

A complete `integrity_check` remains a diagnostic operation rather than a routine startup task.

### 6. Stop RSS after persistent runtime database failure

The plugin does not attempt to replace an open SQLite database while running. After bounded
handling cannot recover a transient error, storage enters an unavailable state for the remainder
of that plugin run:

- scheduled RSS refresh and all feed item writes stop;
- Console RSS endpoints return a service-unavailable response;
- theme-facing RSS lookup returns an empty result so rendering continues;
- link and link-group management remain operational; and
- Link deletion waits when feed-cache cleanup cannot complete.

The next plugin startup closes the failure loop through validation, snapshot recovery, or empty
database creation. Individual migration skips and snapshot failures are logged but do not create
Console warnings; actual RSS unavailability remains explicit to Console API callers.

### 7. Package and validate native SQLite support

Use Xerial SQLite JDBC as a normal plugin runtime dependency and retain its native libraries in the
plugin artifact. A build verification task checks the JDBC service metadata, driver class, and the
packaged Linux glibc/musl, macOS, and Windows x86_64/ARM64 native entries. This structural check
does not replace runtime qualification. Release validation still covers Linux glibc and musl on
x86_64 and ARM64, plus macOS development, plugin restart, and hot reload. SQLite storage is
supported only on a local filesystem with reliable locking. Native extraction must work when the
system temporary directory is mounted `noexec`, using an explicitly controlled
writable/executable location if the driver requires it.

## Risks / Trade-offs

- **A skipped or unreadable legacy record loses RSS data and saved flags** → Log identifying
  information when available, migrate all remaining rows, and retain the original legacy files.
- **A completely unreadable Nitrite database produces an empty cache** → Preserve the failed
  source for diagnosis and let normal refresh refill the cache.
- **A daily snapshot can lose changes since the previous day** → This is accepted because the
  entire RSS store is classified as reconstructable cache data.
- **SQLite and Nitrite together increase the migration release artifact size** → Retain Nitrite
  only because direct legacy upgrades require it; reconsider removal in a later change.
- **One serialized connection limits peak concurrency** → Existing access is already serialized,
  feed batches are bounded, and measured workloads are within the target cache size.
- **`synchronous=FULL` may reduce write throughput** → RSS writes are background and batched;
  durability and predictable crash recovery take priority over maximum throughput.
- **Automatic reset can hide data loss from the UI** → Emit structured error logs and preserve the
  most recent failed file group, consistent with the decision not to show cache-loss warnings.
- **Filesystem or native-library behavior differs by deployment** → Gate release on the declared
  platform matrix and reject unsupported network filesystems in documentation.

## Migration Plan

1. Ship the SQLite schema, store, lifecycle component, and migration reader in one release.
2. On first startup, stop before enabling RSS consumers and execute the migration state machine.
3. Promote SQLite only after transactional migration and validation; create the external marker
   and first snapshot.
4. Start RSS endpoints and scheduling against SQLite. Nitrite remains closed and read-only.
5. On rollback to the previous plugin version, the retained Nitrite database represents the
   migration-time state; SQLite-only changes are intentionally not copied back.

## Open Questions

None. Snapshot implementation may choose the Xerial binding of SQLite's backup API, but it must
satisfy the consistency, validation, and atomic-promotion requirements above.
