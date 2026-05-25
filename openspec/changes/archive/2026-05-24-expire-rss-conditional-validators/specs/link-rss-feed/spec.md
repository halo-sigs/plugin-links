## ADDED Requirements

### Requirement: RSS conditional validators expire
The system SHALL periodically force a full RSS/Atom fetch for each configured
feed URL instead of trusting conditional request validators forever.

#### Scenario: Fresh validators are sent with cached items
- **WHEN** an enabled link feed URL has cached items
- **AND** its per-feed RSS status contains `ETag` or `Last-Modified` validators
- **AND** the validators have freshness metadata newer than the validator freshness window
- **THEN** the refresh request includes the usable conditional request headers

#### Scenario: Empty local cache skips validators
- **WHEN** an enabled link feed URL has no cached items
- **AND** its per-feed RSS status contains `ETag` or `Last-Modified` validators
- **THEN** the refresh request does not include `If-None-Match` or `If-Modified-Since`

#### Scenario: Missing freshness metadata skips validators
- **WHEN** an enabled link feed URL has cached items
- **AND** its per-feed RSS status contains `ETag` or `Last-Modified` validators
- **AND** the validators do not have freshness metadata
- **THEN** the refresh request does not include `If-None-Match` or `If-Modified-Since`

#### Scenario: Stale validators are not sent
- **WHEN** an enabled link feed URL has cached items
- **AND** its per-feed RSS status contains `ETag` or `Last-Modified` validators
- **AND** the validators are older than the validator freshness window
- **THEN** the refresh request does not include `If-None-Match` or `If-Modified-Since`

#### Scenario: Real feed response refreshes validator freshness
- **WHEN** a feed URL refresh receives a successful response with a feed body
- **AND** the response contains `ETag` or `Last-Modified` validators
- **THEN** the system stores those validators on the per-feed RSS status
- **AND** the system records the validator freshness time for that feed URL

#### Scenario: Not-modified response preserves validator freshness
- **WHEN** a feed URL refresh receives `304 Not Modified`
- **THEN** the system preserves the previous per-feed validators
- **AND** the system preserves the previous validator freshness time

#### Scenario: Failed refresh preserves validator freshness
- **WHEN** a feed URL refresh fails
- **THEN** the system preserves the previous per-feed validators
- **AND** the system preserves the previous validator freshness time

#### Scenario: Invalid Last-Modified is not sent
- **WHEN** an enabled link feed URL has cached items
- **AND** its stored `Last-Modified` validator contains an invalid 2038 timestamp
- **THEN** the refresh request does not include `If-Modified-Since`
