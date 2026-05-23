## MODIFIED Requirements

### Requirement: Feed discovery
The system SHALL support discovering RSS/Atom feed URLs for a link website URL,
prioritizing Halo default feed paths before HTML link discovery.

#### Scenario: Halo default feed URLs are discovered first
- **WHEN** the user requests feed discovery for a website whose origin exposes a
  valid RSS or Atom document at `/rss.xml` or `/feed/moments/rss.xml`
- **THEN** the system returns the available Halo default feed URLs without
  enabling RSS automatically
- **AND** the system does not fetch or return HTML alternate-link discovery
  results for that request
- **AND** the system does not return a single `feedUrl` field

#### Scenario: Feed links are discovered from HTML after Halo defaults are unavailable
- **WHEN** the user requests feed discovery for a website that does not expose a
  valid RSS or Atom document at `/rss.xml` or `/feed/moments/rss.xml`
- **AND** the website exposes one or more RSS or Atom `<link>` elements
- **THEN** the system returns the discovered feed URLs without enabling RSS
  automatically
- **AND** the system does not return a single `feedUrl` field

#### Scenario: No feed is discovered
- **WHEN** the user requests feed discovery for a website without a valid Halo
  default feed URL or discoverable HTML feed link
- **THEN** the system returns an empty feed URL list and leaves the link RSS
  configuration unchanged
