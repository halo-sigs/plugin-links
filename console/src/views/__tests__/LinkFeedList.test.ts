import type { LinkFeedItem } from "@/api/generated";
import type { LinkFeedItems } from "@/composables/use-link-feed";
import { Dialog, Toast } from "@halo-dev/components";
import { utils } from "@halo-dev/ui-shared";
import { beforeEach, describe, expect, it, rstest } from "@rstest/core";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { computed, nextTick, shallowRef, type ShallowRef } from "vue";
import { createMemoryHistory, createRouter } from "vue-router";
import LinkFeedList from "../LinkFeedList.vue";

const viewMocks = rstest.hoisted(() => ({
  mainFeed: undefined as LinkFeedItems | undefined,
  itemSummary: {
    value: undefined as { hiddenCount: number; favoriteCount: number; readLaterCount: number } | undefined,
  },
  itemSummaryError: { value: false },
  itemSummaryFetching: { value: false },
  setHiddenState: rstest.fn(),
  useLinkFeedItemsCalls: { count: 0 },
}));
const permissionSpy = rstest.spyOn(utils.permission, "has");

rstest.mock("@/composables/use-link-feed", () => ({
  useLinkFeedItems: () => {
    viewMocks.useLinkFeedItemsCalls.count += 1;
    if (viewMocks.useLinkFeedItemsCalls.count === 1) {
      return viewMocks.mainFeed;
    }
    return createFeed([]);
  },
}));

rstest.mock("@/composables/use-link-feed-hidden-state", () => ({
  useLinkFeedHiddenState: () => ({
    isUpdating: shallowRef(false),
    setHiddenState: viewMocks.setHiddenState,
  }),
}));

rstest.mock("@/composables/use-link-feed-item-summary", () => ({
  useLinkFeedItemSummary: () => ({
    data: viewMocks.itemSummary,
    isError: viewMocks.itemSummaryError,
    isFetching: viewMocks.itemSummaryFetching,
  }),
}));

rstest.mock("@/composables/use-link-feed-unread-summary", () => ({
  useLinkFeedUnreadSummary: () => ({ data: shallowRef(undefined) }),
  linkFeedUnreadCount: () => 0,
}));

rstest.mock("@/composables/use-link-fetch", () => ({
  useRssLinksFetch: () => ({ data: shallowRef([]), isLoading: shallowRef(false) }),
}));

rstest.mock("@/composables/use-link-feed-mark-all-read", () => ({
  useLinkFeedMarkAllRead: () => ({ isMarkingAllRead: shallowRef(false), markAllRead: rstest.fn() }),
}));

rstest.mock("@/composables/use-link-feed-refresh", () => ({
  useLinkFeedRefresh: () => ({
    isRefreshing: shallowRef(false),
    totalCount: shallowRef(0),
    completedCount: shallowRef(0),
    refreshLinks: rstest.fn(),
  }),
}));

rstest.mock("@/composables/use-link-feed-item-actions", () => ({
  useLinkFeedItemActions: () => ({
    isMarkingFavorite: { value: false },
    isMarkingRead: { value: false },
    isMarkingReadLater: { value: false },
    openItem: rstest.fn(),
    toggleFavorite: rstest.fn(),
    toggleRead: rstest.fn(),
    toggleReadLater: rstest.fn(),
  }),
}));

rstest.mock("@vueuse/core", () => ({
  useIntersectionObserver: () => ({
    isSupported: shallowRef(false),
    stop: () => {},
  }),
}));

describe("LinkFeedList hidden workflows", () => {
  beforeEach(() => {
    permissionSpy.mockReturnValue(true);
    viewMocks.useLinkFeedItemsCalls.count = 0;
    viewMocks.itemSummary.value = { hiddenCount: 3, favoriteCount: 5, readLaterCount: 4 };
    viewMocks.itemSummaryError.value = false;
    viewMocks.itemSummaryFetching.value = false;
    viewMocks.mainFeed = createFeed([feedItem({ id: "item-1" }), feedItem({ id: "item-2", title: "Second" })]);
    viewMocks.setHiddenState.mockResolvedValue({ requestedCount: 2, updatedCount: 2 });
  });

  it("shows all exact item counts in the existing header order", () => {
    mountView();

    const entry = viewButton("已隐藏");
    const favoriteEntry = viewButton("收藏");
    const readLaterEntry = viewButton("稍后阅读");
    expect(readLaterEntry?.textContent).toContain("稍后阅读 (4)");
    expect(favoriteEntry?.textContent).toContain("收藏 (5)");
    expect(entry?.textContent).toContain("已隐藏 (3)");
    const buttons = Array.from(viewRoot().querySelectorAll<HTMLButtonElement>("button"));
    expect(buttons.indexOf(readLaterEntry!)).toBeLessThan(buttons.indexOf(favoriteEntry!));
    expect(buttons.indexOf(favoriteEntry!)).toBeLessThan(buttons.indexOf(entry!));
  });

  it("shows exact zero counts after the query resolves", () => {
    viewMocks.itemSummary.value = { hiddenCount: 0, favoriteCount: 0, readLaterCount: 0 };

    mountView();

    expect(viewButton("稍后阅读")?.textContent).toContain("稍后阅读 (0)");
    expect(viewButton("收藏")?.textContent).toContain("收藏 (0)");
    expect(viewButton("已隐藏")?.textContent).toContain("已隐藏 (0)");
  });

  it("shows zero placeholders while pending and keeps labels usable when unavailable", () => {
    viewMocks.itemSummary.value = undefined;
    viewMocks.itemSummaryFetching.value = true;

    mountView();

    expect(viewButton("稍后阅读")?.textContent?.trim()).toBe("稍后阅读 (0)");
    expect(viewButton("收藏")?.textContent?.trim()).toBe("收藏 (0)");
    expect(viewButton("已隐藏")?.textContent?.trim()).toBe("已隐藏 (0)");

    mountedWrapper?.unmount();
    viewMocks.itemSummaryFetching.value = false;
    viewMocks.itemSummaryError.value = true;
    mountView();

    expect(viewButton("稍后阅读")?.textContent?.trim()).toBe("稍后阅读");
    expect(viewButton("收藏")?.textContent?.trim()).toBe("收藏");
    expect(viewButton("已隐藏")?.textContent?.trim()).toBe("已隐藏");
  });

  it("preserves resolved counts during a background refetch", () => {
    viewMocks.itemSummaryFetching.value = true;

    mountView();

    expect(viewButton("稍后阅读")?.textContent?.trim()).toBe("稍后阅读 (4)");
    expect(viewButton("收藏")?.textContent?.trim()).toBe("收藏 (5)");
    expect(viewButton("已隐藏")?.textContent?.trim()).toBe("已隐藏 (3)");
  });

  it("keeps hidden items visible but hides mutation controls without manage permission", async () => {
    permissionSpy.mockReturnValue(false);

    mountView();
    await nextTick();

    expect(viewButton("已隐藏")).toBeDefined();
    expect(viewButton("批量选择")).toBeUndefined();
    expect(viewButton("隐藏文章")).toBeUndefined();
  });

  it("hides a single item after confirmation", async () => {
    viewMocks.setHiddenState.mockResolvedValue({ requestedCount: 1, updatedCount: 1 });
    const dialogSpy = rstest.spyOn(Dialog, "warning");
    const toastSpy = rstest.spyOn(Toast, "success");
    mountView();
    await nextTick();

    viewButton("隐藏文章")?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await nextTick();

    expect(dialogSpy).toHaveBeenCalledTimes(1);
    const dialogOptions = dialogSpy.mock.calls[0][0] as { description?: string; onConfirm?: () => Promise<void> };
    expect(dialogOptions.description).toContain("Example article");
    expect(viewMocks.setHiddenState).not.toHaveBeenCalled();

    await dialogOptions.onConfirm?.();
    await flushPromises();

    expect(viewMocks.setHiddenState).toHaveBeenCalledWith(["item-1"], true);
    expect(toastSpy).toHaveBeenCalledWith("已隐藏 1 篇文章");
  });

  it("supports batch hide mode with select-all-loaded, confirmation and cleanup", async () => {
    const dialogSpy = rstest.spyOn(Dialog, "warning");
    const toastSpy = rstest.spyOn(Toast, "success");
    mountView();
    await nextTick();

    expect(document.querySelectorAll('input[type="checkbox"]')).toHaveLength(0);

    expect(viewButton("批量选择")).toBeDefined();
    expect(viewButton("批量隐藏")).toBeUndefined();
    viewButton("批量选择")?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await nextTick();

    expect(document.querySelectorAll('input[type="checkbox"]')).toHaveLength(2);
    expect(viewButton("隐藏所选")?.textContent).toContain("隐藏所选");

    viewButton("全选已加载")?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await nextTick();

    const checkboxes = Array.from(document.querySelectorAll<HTMLInputElement>('input[type="checkbox"]'));
    expect(checkboxes.every((checkbox) => checkbox.checked)).toBe(true);
    expect(viewRoot().textContent).toContain("已选 2 篇");

    viewButton("隐藏所选")?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await nextTick();

    expect(dialogSpy).toHaveBeenCalledTimes(1);
    const dialogOptions = dialogSpy.mock.calls[0][0] as { description?: string; onConfirm?: () => Promise<void> };
    expect(dialogOptions.description).toContain("2 篇文章");
    expect(viewMocks.setHiddenState).not.toHaveBeenCalled();

    await dialogOptions.onConfirm?.();
    await flushPromises();

    expect(viewMocks.setHiddenState).toHaveBeenCalledWith(["item-1", "item-2"], true);
    expect(toastSpy).toHaveBeenCalledWith("已隐藏 2 篇文章");
    expect(viewButton("批量选择")).toBeDefined();
    expect(document.querySelectorAll('input[type="checkbox"]')).toHaveLength(0);
  });

  it("cancels batch hide mode without changes", async () => {
    const dialogSpy = rstest.spyOn(Dialog, "warning");
    mountView();
    await nextTick();

    viewButton("批量选择")?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await nextTick();
    viewButton("取消")?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await nextTick();

    expect(document.querySelectorAll('input[type="checkbox"]')).toHaveLength(0);
    expect(dialogSpy).not.toHaveBeenCalled();
    expect(viewMocks.setHiddenState).not.toHaveBeenCalled();
  });

  it("clears the current selection when the read filter changes", async () => {
    mountView();
    await nextTick();

    viewButton("批量选择")?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await nextTick();
    viewButton("全选已加载")?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await nextTick();
    expect(viewRoot().textContent).toContain("已选 2 篇");

    const unreadTab = Array.from(document.querySelectorAll<HTMLButtonElement>(".feed-status-tabs__item")).find((tab) =>
      tab.textContent?.includes("未读"),
    );
    unreadTab?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await nextTick();

    expect(viewRoot().textContent).toContain("已选 0 篇");
    const checkboxes = Array.from(document.querySelectorAll<HTMLInputElement>('input[type="checkbox"]'));
    expect(checkboxes.every((checkbox) => !checkbox.checked)).toBe(true);
  });

  it("clears the current selection when the list reloads", async () => {
    mountView();
    await nextTick();

    viewButton("批量选择")?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await nextTick();
    viewButton("全选已加载")?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await nextTick();

    viewButton("刷新列表")?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await flushPromises();

    expect(viewMocks.mainFeed?.reload).toHaveBeenCalledTimes(1);
    expect(viewRoot().textContent).toContain("已选 0 篇");
  });
});

let mountedWrapper: VueWrapper | undefined;

function mountView() {
  mountedWrapper?.unmount();
  document.body.innerHTML = "";
  const host = document.createElement("div");
  document.body.appendChild(host);
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/", name: "LinkFeed", component: { template: "<div />" } },
      { path: "/links", name: "Links", component: { template: "<div />" } },
    ],
  });
  mountedWrapper = mount(LinkFeedList, {
    attachTo: host,
    global: {
      plugins: [router],
    },
  });
  return mountedWrapper;
}

function viewRoot(): HTMLElement {
  return document.body;
}

function viewButton(text: string): HTMLButtonElement | undefined {
  const candidates = Array.from(viewRoot().querySelectorAll<HTMLButtonElement>("button"));
  return (
    candidates.find((element) => element.textContent?.includes(text) && !element.getAttribute("aria-label")) ||
    (Array.from(viewRoot().querySelectorAll<HTMLElement>("[aria-label]")).find((element) =>
      element.getAttribute("aria-label")?.includes(text),
    ) as HTMLButtonElement | undefined)
  );
}

function createFeed(items: LinkFeedItem[]): LinkFeedItems {
  const itemsRef = shallowRef(items) as ShallowRef<LinkFeedItem[]>;
  const readStatus = shallowRef("" as "" | "unread" | "read");
  return {
    items: computed(() => itemsRef.value),
    selectedLinkName: shallowRef(""),
    selectedReadStatus: readStatus,
    hasNext: computed(() => false),
    isLoading: computed(() => false),
    isFetching: computed(() => false),
    isLoadingMore: computed(() => false),
    reload: rstest.fn(),
    fetchNextPage: rstest.fn(),
    selectLink: rstest.fn(),
    selectReadStatus: (status: "" | "unread" | "read") => {
      readStatus.value = status;
    },
  } as unknown as LinkFeedItems;
}

function feedItem(overrides: Partial<LinkFeedItem> = {}): LinkFeedItem {
  return {
    id: "item-1",
    title: "Example article",
    url: "https://example.com/article-1",
    read: false,
    favorite: false,
    readLater: false,
    hidden: false,
    ...overrides,
  } as LinkFeedItem;
}
