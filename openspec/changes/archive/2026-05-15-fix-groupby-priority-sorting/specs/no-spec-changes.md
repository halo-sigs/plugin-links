No spec-level API contract changes are introduced by this fix.

The change is purely an internal sorting correction in `LinkPublicQueryServiceImpl.groupComparator()` to align LinkGroup ordering with the existing ascending-priority semantics used by `defaultLinkSort()`, the frontend, and the `sortLinkGroups` endpoint.
