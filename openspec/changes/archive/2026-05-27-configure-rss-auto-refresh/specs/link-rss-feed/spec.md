## ADDED Requirements

### Requirement: Scheduled feed refresh settings
The system SHALL expose plugin settings that control scheduled RSS refresh behavior while keeping
link-level RSS subscriptions under `Link.spec.rss`.

#### Scenario: Scheduled RSS refresh is enabled by default
- **WHEN** the plugin settings have not been customized
- **THEN** scheduled RSS refresh is enabled

#### Scenario: Configure scheduled RSS refresh interval
- **WHEN** an administrator configures the scheduled RSS refresh interval
- **THEN** the system uses that interval to decide when a scheduled RSS refresh run is due

#### Scenario: Configure scheduled RSS refresh batch size
- **WHEN** an administrator configures the maximum links per scheduled RSS refresh run
- **THEN** one scheduled RSS refresh run processes no more than that number of links

#### Scenario: Disable scheduled RSS refresh globally
- **WHEN** an administrator disables scheduled RSS refresh
- **THEN** the scheduler does not refresh RSS feeds automatically
- **AND** existing link-level RSS settings remain unchanged

## MODIFIED Requirements

### Requirement: Scheduled feed refresh
The system SHALL refresh enabled link feeds on a configurable background schedule when scheduled
RSS refresh is enabled.

#### Scenario: Scheduled refresh processes enabled links
- **WHEN** scheduled RSS refresh is enabled and a scheduled refresh run is due
- **THEN** the system refreshes links whose `spec.rss.enabled` is `true` and
  whose `spec.rss.feedUrls` contains at least one feed URL

#### Scenario: Scheduled refresh processes every configured feed URL
- **WHEN** the scheduled RSS refresh processes an enabled link with multiple
  configured feed URLs
- **THEN** the system attempts to refresh each configured feed URL for that link

#### Scenario: Scheduled refresh skips disabled links
- **WHEN** the scheduled RSS refresh runs
- **THEN** the system does not fetch feeds for links whose `spec.rss.enabled` is absent or `false`

#### Scenario: Scheduled refresh skips links without feed URLs
- **WHEN** the scheduled RSS refresh runs
- **THEN** the system does not fetch feeds for enabled links whose
  `spec.rss.feedUrls` is absent, empty, or only blank

#### Scenario: Scheduled refresh does not run while globally disabled
- **WHEN** scheduled RSS refresh is disabled in plugin settings
- **THEN** the scheduler does not fetch any feed URLs automatically

#### Scenario: Scheduled refresh respects per-run limit
- **WHEN** more eligible RSS-enabled links exist than the configured maximum links per run
- **THEN** the scheduled refresh run processes no more than the configured maximum number of links

#### Scenario: Scheduled refresh prefers stale RSS status
- **WHEN** more eligible RSS-enabled links exist than the configured maximum links per run
- **THEN** the scheduler prioritizes links whose `status.rss.lastFetchedAt` is absent or oldest
