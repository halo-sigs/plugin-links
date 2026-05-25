## ADDED Requirements

### Requirement: Link deletion feed cache cleanup
The system SHALL remove cached RSS/Atom feed items that belong to a `Link` when that `Link` is deleted.

#### Scenario: Deleted link cache is removed
- **WHEN** a `Link` enters deletion and RSS/Atom feed items are cached with that link's metadata name
- **THEN** the system removes those cached feed items before the `Link` deletion is finalized

#### Scenario: Saved items do not survive link deletion
- **WHEN** a deleted `Link` has cached RSS/Atom feed items marked as read, favorite, or read-later
- **THEN** the system removes those cached feed items together with the rest of that link's feed cache

#### Scenario: Link without cached feed items is deleted
- **WHEN** a `Link` enters deletion and no RSS/Atom feed items are cached with that link's metadata name
- **THEN** the system finalizes the `Link` deletion without creating feed item records

#### Scenario: Normal retention still protects saved items
- **WHEN** normal RSS/Atom cache retention cleanup runs for links that are not being deleted
- **THEN** the system continues to protect cached feed items whose favorite or read-later state is true
