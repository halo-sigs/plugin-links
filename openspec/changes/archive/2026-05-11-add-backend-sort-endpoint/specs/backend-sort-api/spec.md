## ADDED Requirements

### Requirement: Link sort endpoint
The system SHALL expose a POST endpoint at `/apis/api.plugin.halo.run/v1alpha1/plugins/PluginLinks/links/-/sort` that accepts an ordered list of link names and updates each link's `spec.priority` to match its position in the list.

#### Scenario: Successful link reordering
- **WHEN** the frontend sends a POST request to the link sort endpoint with a body containing `["link-a", "link-c", "link-b"]`
- **THEN** the system updates `link-a` priority to 0, `link-c` priority to 1, and `link-b` priority to 2
- **AND** the endpoint returns HTTP 200 OK

#### Scenario: Empty link sort request
- **WHEN** the frontend sends a POST request to the link sort endpoint with an empty list
- **THEN** the endpoint returns HTTP 200 OK without modifying any links

### Requirement: Link group sort endpoint
The system SHALL expose a POST endpoint at `/apis/api.plugin.halo.run/v1alpha1/plugins/PluginLinks/link-groups/-/sort` that accepts an ordered list of link group names and updates each group's `spec.priority` to match its position in the list.

#### Scenario: Successful link group reordering
- **WHEN** the frontend sends a POST request to the link group sort endpoint with a body containing `["group-b", "group-a"]`
- **THEN** the system updates `group-b` priority to 0 and `group-a` priority to 1
- **AND** the endpoint returns HTTP 200 OK

### Requirement: Sort request schema
The sort endpoint request body SHALL be a JSON object containing a single field `names` which is an array of non-empty strings representing the ordered list of extension names.

#### Scenario: Invalid sort request with non-array names
- **WHEN** the frontend sends a POST request with a body where `names` is not an array
- **THEN** the endpoint returns HTTP 400 Bad Request
