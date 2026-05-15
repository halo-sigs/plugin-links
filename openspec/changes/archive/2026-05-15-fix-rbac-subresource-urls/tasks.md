## 1. Fix RBAC Subresource Rules

- [x] 1.1 Update `roleTemplate.yaml` to replace `nonResourceURLs` with `resources` + `resourceNames: ["-"]` for sort and detail endpoints
- [x] 1.2 Verify the YAML is valid and matches Halo core role template conventions
