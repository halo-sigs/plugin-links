## Context

The `/links` route in `LinkRouter` currently builds a model map with three entries: `groups` (a `Mono<List<LinkGroupVo>>`), `pluginName`, and `linksTitle`. The `groups` variable is fully aggregated — each `LinkGroupVo` contains its nested `LinkVo` list. This is produced by `LinkFinder.groupBy()`, which performs an N+1 query pattern (one query for all groups, then one query per group for its links).

Plugin-photos (`PhotoRouter`) uses `org.thymeleaf.context.LazyContextVariable` to defer data loading until the template actually references a variable. This avoids expensive queries for variables the template doesn't use.

## Goals / Non-Goals

**Goals:**
- Expose `simpleGroups` — a lightweight group list without nested links.
- Expose `links` — a flat list of links, filterable by `?group=` query parameter.
- Make `groups`, `simpleGroups`, and `links` lazy-loaded via `LazyContextVariable`.
- Add `_templateId` to the model for Halo theme compatibility.
- Maintain full backward compatibility for existing theme templates using `groups`.

**Non-Goals:**
- No frontend (Console) changes.
- No changes to `LinkFinder` theme-side API.
- No database schema or extension model changes.
- No pagination for `links` (friend-link pages are typically flat lists).

## Decisions

### 1. Use `LazyContextVariable` for all data variables
**Rationale:** This is the established pattern in plugin-photos and aligns with Halo's theme rendering conventions. It ensures that `groupBy()`'s N+1 queries are only executed when a template actually renders `groups`. Templates that only need `links` or `simpleGroups` skip the expensive aggregation entirely.

### 2. `LinkRouter` directly injects `LinkPublicQueryService`
**Rationale:** `simpleGroups` needs `listAllGroups()` and `links` needs `listLinks()` — both live on `LinkPublicQueryService`. Rather than adding new methods to `LinkFinder` (which is a theme-facing API), we follow plugin-photos' pattern and inject the query service directly into the router. This keeps the `LinkFinder` contract stable.

### 3. `simpleGroups` excludes the virtual `ungrouped` group
**Rationale:** `ungrouped` is a synthetic group created in `LinkFinderImpl` for links without a `groupName`. It is not a real `LinkGroup` extension and therefore cannot be returned by `listAllGroups()`. Templates that need to show ungrouped links can use the `links` variable directly.

### 4. `links` returns all matching links (no pagination)
**Rationale:** Friend-link pages typically display all links at once. Pagination would add unnecessary complexity. If a site has enough links to warrant pagination, that can be added as a future enhancement.

### 5. Block timeout of 10 seconds
**Rationale:** Matches plugin-photos' `BLOCKING_TIMEOUT` value. This is a defensive measure for the `.block()` call inside `LazyContextVariable.loadValue()`.

## Risks / Trade-offs

- **[Risk]** `LazyContextVariable.loadValue()` calls `.block()`, which blocks the rendering thread. If the reactive pipeline hangs, the page times out after 10 seconds.  
  **→ Mitigation:** Timeout is already bounded. The underlying queries use Halo's indexed extension client and are typically sub-second.

- **[Risk]** Existing templates rely on `groups` being eagerly resolved. Switching to lazy loading changes the timing of the query but not the observable behavior.  
  **→ Mitigation:** `groups` retains the same type (`List<LinkGroupVo>`) and semantics. The lazy loading is transparent to templates.

- **[Trade-off]** `links` without pagination could be slow for very large link collections.  
  **→ Mitigation:** Friend-link collections are typically small (< 1000). If this becomes a problem, pagination can be added later without breaking the template contract.
