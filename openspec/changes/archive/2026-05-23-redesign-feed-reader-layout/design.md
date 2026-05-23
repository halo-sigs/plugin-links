## Context

The RSS updates page currently combines several independent concerns in one vertical flow:

```text
page header
read-later section
link/group/read-state filter bar
article list
favorite modal
```

This makes the page feel like a management screen instead of a feed reader. The backend already supports the needed filters: `linkName` for source selection and `read` for all/unread/read filtering. The Console also already has `useRssLinksFetch()` to fetch only subscribed links via `fieldSelector: ["spec.rss.enabled=true"]` and a local `feedUrl` guard. The redesign can therefore stay frontend-only.

## Goals / Non-Goals

**Goals:**

- Make the RSS updates page read like a two-column feed reader.
- Put subscribed link navigation in a left sidebar.
- Put all/unread/read controls above the article list as tabs.
- Remove group filtering from the RSS updates UI.
- Keep existing item interactions: open article, mark read/unread, favorite, read-later, and load more.
- Preserve existing backend APIs and generated clients.

**Non-Goals:**

- No backend RSS query changes.
- No new feed item persistence or saved-state model.
- No per-user reader preferences.
- No full RSS reader features such as folders, keyboard shortcuts, source unread counts, or virtualized infinite scrolling.
- No redesign of the main link-management page.

## Decisions

### Use a subscription sidebar instead of a link dropdown

Render a left sidebar with an "all updates" entry followed by subscribed links from `useRssLinksFetch()`. Selecting an entry calls the existing `selectLink()` path with either an empty link name or a specific link metadata name.

Rationale:

- A source list is easier to scan than a dropdown when the page is primarily for reading updates.
- The existing subscribed-link fetch path already limits choices to RSS-enabled links with feed URLs.
- Keeping `linkName` as the underlying filter avoids backend churn.

Alternatives considered:

- Keep a dropdown in a top filter bar: smaller code change, but preserves the management-form feel.
- Group subscriptions in the sidebar: rejected for this change because the user explicitly wants groups removed from the dynamic UI.

### Replace read-state select with tabs

Render all, unread, and read as tabs directly above the article list. The selected tab continues to map to the existing `LinkFeedReadStatus` values: `""`, `"unread"`, and `"read"`.

Rationale:

- Read state is a primary browsing mode and should be visible without opening a select.
- Tabs match the requested right-column ordering: read-state controls first, article list second.
- The existing composable can continue to own read-state filtering.

Alternatives considered:

- Use segmented buttons: visually close to tabs, but less consistent with the "filter tab" language in the requirement.
- Keep a native select for mobile only: unnecessary unless the tab labels prove too wide.

### Remove group state from the primary feed UI, not from the backend

The RSS updates view should stop exposing group filtering and stop managing `selectedGroupName` in its primary interaction path. Backend `groupName` query support can remain for API compatibility and future use.

Rationale:

- The user wants the dynamic UI to drop groups.
- Removing the UI control is enough to simplify the reader workflow.
- Keeping the API avoids unnecessary generated-client and backend test churn.

Alternatives considered:

- Delete backend group filtering: broader and riskier than the UX change requires.
- Hide group filtering only on small screens: keeps the conceptual clutter on desktop.

### Keep the right column sequence clean

The right content column should render read-state tabs followed by the article list. Secondary saved workflows, such as favorites and read-later, should remain available through separate surfaces that do not insert a read-later list above the primary feed.

Rationale:

- The requested sequence is explicit and should stay visually stable.
- Saved workflows are useful, but they should not interrupt source-and-read-state browsing.
- Existing saved-state APIs can still power secondary surfaces.

Alternatives considered:

- Keep the current read-later section above the feed: conflicts with the requested layout.
- Add a permanent right rail for saved items: competes with article width and repeats an option previously rejected for the saved-workflow refinement.

### Split RSS reader UI into focused components during implementation

Keep `LinkFeedList.vue` as the route-level composition surface and extract focused presentational pieces if the implementation becomes bulky:

- `LinkFeedSubscriptionSidebar`: all updates entry and subscribed link list.
- `LinkFeedReadStatusTabs`: all/unread/read tab controls.
- `LinkFeedItemList`: loading, empty state, item cards, and load-more control.

Rationale:

- The current view already coordinates three feed states and a modal. Splitting the layout pieces keeps the route component readable.
- Props-down/events-up contracts match the existing Vue 3 Composition API style.

## Risks / Trade-offs

- [Risk] The sidebar could become cramped on narrow Console viewports. -> Mitigation: stack sidebar above content on small screens or make it horizontally scrollable while preserving source selection.
- [Risk] Removing group filtering from the UI may surprise users who used it for browsing. -> Mitigation: keep all subscribed links visible and keep backend support intact; this change only removes the RSS updates UI control.
- [Risk] Moving read-later out of the primary vertical flow could make it less discoverable. -> Mitigation: keep a stable secondary entry, but do not place the read-later list between the tabs and article list.
- [Risk] Item state can drift across main, favorite, and read-later surfaces. -> Mitigation: keep the current `patchItemState()` synchronization pattern when extracting components.
