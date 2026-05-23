## Context

The RSS updates page currently treats saved states as ordinary filters in the main feed
filter bar. This works mechanically because the backend already supports `favorite` and
`readLater` query parameters, but the interaction model is muddier than the data model:

```text
main feed filters       saved-item workflows
-----------------       --------------------
link                    favorite collection
group                   read-later queue
read/unread
```

Favorite is a long-lived saved collection. Read-later is a short-lived work queue. Both
should be easy to reach without replacing the main feed filter context.

## Goals / Non-Goals

**Goals:**

- Make favorite and read-later feel like independent saved-item workflows.
- Keep the primary RSS updates filter bar focused on browsing the main feed.
- Surface read-later items immediately on the RSS updates page.
- Provide a stable favorite entry from the page header.
- Reuse existing saved-state API parameters and item toggle endpoints.
- Keep the existing "open article marks read and clears read-later" behavior.

**Non-Goals:**

- Change the backend feed item data model.
- Add per-user saved state isolation.
- Add new persistence, new API resources, or new generated client methods unless the
  implementation discovers a gap.
- Redesign the whole RSS updates page or the links list.

## Decisions

### Put favorites behind a page-header action

Add a compact star action to the `VPageHeader` actions area. Clicking it opens a modal with
favorite feed items loaded through the existing list endpoint using `favorite=true`.

Rationale:
- Favorite is a durable collection that users may open intentionally.
- Header placement keeps the entry stable and independent from current main-list filters.
- A modal avoids taking over the page and keeps the user in the RSS updates context.

Alternatives considered:
- Keep favorite as a filter: simple, but hides the feature behind a browsing control.
- Add a separate route: heavier navigation for a personal-blog plugin use case.
- Use a right sidebar: more persistent, but competes with the feed content and is awkward on
  narrow Console viewports.

### Put read-later near the top of the page

Add a read-later section above the main feed list. It queries `readLater=true` independently
from the main feed and displays pending items in a compact list. The section should provide a
"view all" affordance only if the compact list is capped.

Rationale:
- Read-later is a queue; it should be visible before the user starts browsing fresh updates.
- Top placement works better across desktop and mobile than a fixed right column.
- Independent querying prevents read-later state from disturbing the main feed cursor and
  filters.

Alternatives considered:
- Right-side panel: good for wide desktop, but reduces scan width and requires a responsive
  collapse strategy.
- Keep read-later as a filter: mechanically cheap, but makes users hunt for the queue.

### Keep saved-state filters in API, remove them from primary UI filters

The API-level `favorite` and `readLater` filters remain useful for the favorite modal and
read-later section. The primary filter bar should remove the favorite/read-later dropdowns
and keep only link, group, read state, and reload controls.

Rationale:
- No backend contract churn.
- The main feed remains a chronological browsing surface.
- Saved item surfaces can refresh independently when item state changes.

### Refresh saved surfaces after saved-state mutations

When a user toggles favorite or read-later from any surface, the visible surfaces affected by
that change should update immediately. For example, removing read-later from the read-later
section removes that row from the section; favoriting an item in the main feed makes it appear
the next time the favorite modal is opened or refreshed.

Rationale:
- Saved-state surfaces should feel live without requiring a page reload.
- Existing optimistic item mutation behavior can be retained, but it should be scoped to each
  list's purpose rather than to the old global saved-state filters.

## Risks / Trade-offs

- [Risk] The page could make three independent list requests on load if main feed, read-later,
  and favorite counts all load eagerly. -> Mitigation: load the main feed and read-later section
  eagerly, and load favorite items only when the modal opens.
- [Risk] Duplicate item cards across main feed and read-later section could feel noisy. ->
  Mitigation: render read-later as a compact queue with fewer details than the main feed.
- [Risk] Updating item state across multiple visible lists can drift. -> Mitigation: centralize
  item state mutation helpers and explicitly remove items from saved surfaces when their saved
  state no longer matches.
- [Risk] A modal with many favorites could become unwieldy. -> Mitigation: use the existing
  cursor pagination endpoint and a load-more control in the modal if needed.
