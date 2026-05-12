## ADDED Requirements

### Requirement: Template variables are loaded on-demand
The `/links` theme route SHALL load `groups`, `simpleGroups`, and `links` variables on-demand using Thymeleaf's `LazyContextVariable`. A variable SHALL NOT trigger a database query unless the template references it.

#### Scenario: Template only uses links
- **WHEN** a theme template renders `/links` and only references `${links}`
- **THEN** the system queries links once and does not query groups or simpleGroups

#### Scenario: Template uses all three variables
- **WHEN** a theme template renders `/links` and references `${groups}`, `${simpleGroups}`, and `${links}`
- **THEN** the system queries each variable independently when first referenced

### Requirement: Links variable supports group filtering
The `/links` route SHALL expose a `links` variable containing a list of `LinkVo` objects. The route SHALL accept an optional `group` query parameter. When present, `links` SHALL contain only links belonging to that group. When absent, `links` SHALL contain all links. The current group filter value SHALL be exposed as a `group` string variable in the model.

#### Scenario: No group filter
- **WHEN** a request is made to `/links` without a `group` parameter
- **THEN** the `links` variable contains all links sorted by priority, creation timestamp, and name

#### Scenario: Group filter applied
- **WHEN** a request is made to `/links?group=friends`
- **THEN** the `links` variable contains only links whose `spec.groupName` equals "friends"
- **AND** the `group` model variable equals "friends"

### Requirement: Simple groups variable provides lightweight group list
The `/links` route SHALL expose a `simpleGroups` variable containing a list of `LinkGroupVo` objects without nested links. The list SHALL be sorted by group priority, creation timestamp, and name. The virtual `ungrouped` group SHALL NOT be included.

#### Scenario: Render simpleGroups
- **WHEN** a theme template references `${simpleGroups}`
- **THEN** the variable contains all real `LinkGroup` extensions sorted by priority and timestamp
- **AND** each group object has an empty `links` list
- **AND** the synthetic `ungrouped` group is not present

### Requirement: Template ID is present in model
The `/links` route SHALL include `_templateId` with value `"links"` in the model map.

#### Scenario: Render links page
- **WHEN** any request is made to `/links`
- **THEN** the model contains `_templateId` set to `"links"`
