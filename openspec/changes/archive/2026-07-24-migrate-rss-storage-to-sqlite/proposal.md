## Why

The RSS feed item cache currently depends on Nitrite, whose file stability and recovery
behavior have proved unreliable for this plugin. Replacing it with SQLite gives the cache a
well-understood transactional storage engine while preserving the existing RSS workflows and
providing a bounded, automatic recovery path when local cache files are damaged.

## What Changes

- Replace the Nitrite-backed RSS feed item store with a SQLite-backed implementation while
  preserving all existing feed item fields, saved states, query ordering, filtering, and
  retention behavior.
- Migrate an existing Nitrite database into a validated temporary SQLite database before an
  atomic cutover, without modifying the readable Nitrite source, and fall back to retained
  Nitrite JSON backups when the live legacy file cannot be read.
- Make migration tolerant of unreadable individual Nitrite records and able to finish with an
  empty SQLite cache when the legacy database cannot be opened at all.
- Record a durable migration-complete marker so a later SQLite reset never imports stale Nitrite
  data again, and retain the legacy Nitrite files as a read-only migration snapshot.
- Create and validate an initial SQLite snapshot after migration and daily snapshots thereafter,
  retaining the two newest valid snapshots.
- Check SQLite consistency when the plugin starts, restore the newest valid snapshot
  automatically when necessary, and create an empty database after isolating unrecoverable files
  when no snapshot is valid.
- Keep link management available when RSS storage is unavailable, while stopping RSS access and
  refresh work until the next plugin restart can run recovery.
- Stop using Nitrite for active RSS persistence, retain its libraries only for the one-time legacy
  migration reader, and package the SQLite JDBC driver and its supported native libraries with the
  plugin.
- Release plugin-classloader SQLite JDBC registrations during shutdown so repeated plugin reloads
  do not retain an obsolete plugin classloader.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `link-rss-feed`: Define SQLite-backed feed item persistence, one-time Nitrite migration,
  snapshot creation, startup consistency recovery, and RSS-only failure isolation.

## Impact

- Backend RSS persistence, database lifecycle, maintenance scheduling, link-deletion cleanup, and
  plugin startup/shutdown behavior will change.
- Gradle dependencies and the packaged plugin artifact will add Xerial SQLite JDBC and bundled
  platform-native libraries. Nitrite remains packaged only so installations can migrate directly
  from a legacy database.
- Existing Console RSS APIs retain their request and response contracts, but return an explicit
  service error while RSS storage is unavailable; link and link-group APIs remain available.
- Theme-facing RSS lookup remains non-fatal and returns no cached entries while storage is
  unavailable.
- Existing installations receive a one-time local data migration. No Halo Extension resources or
  link RSS configuration fields are changed.
