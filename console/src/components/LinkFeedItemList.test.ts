import type { LinkFeedItem } from "@/api/generated";
import type { LinkFeedItems } from "@/composables/use-link-feed";
import { VLoading } from "@halo-dev/components";
import { describe, expect, it, rstest } from "@rstest/core";
import { VueQueryPlugin } from "@tanstack/vue-query";
import { mount } from "@vue/test-utils";
import { computed, shallowRef } from "vue";
import { createFeedTestQueryClient } from "../composables/link-feed-test-utils";
import LinkFeedItemList from "./LinkFeedItemList.vue";

rstest.mock("@vueuse/core", () => ({
  useIntersectionObserver: () => ({
    isSupported: shallowRef(false),
    stop: () => {},
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

describe("LinkFeedItemList", () => {
  it("shows the loading indicator while the first page loads", () => {
    const wrapper = mountList({ feed: fakeFeed({ isLoading: true }) });

    expect(wrapper.findComponent(VLoading).exists()).toBe(true);
    expect(wrapper.text()).not.toContain("暂无数据");
  });

  it("shows the empty state when no items are loaded", () => {
    const wrapper = mountList({ feed: fakeFeed(), emptyText: "暂无已隐藏文章" });

    expect(wrapper.text()).toContain("暂无已隐藏文章");
  });

  it("renders checkboxes for every loaded item in selection mode", () => {
    const wrapper = mountList({
      feed: fakeFeed({ items: [feedItem({ id: "item-1" }), feedItem({ id: "item-2", title: "Second" })] }),
      selectable: true,
      selectedIds: ["item-2"],
    });

    const checkboxes = wrapper.findAll('input[type="checkbox"]');
    expect(checkboxes).toHaveLength(2);
    expect((checkboxes[0].element as HTMLInputElement).checked).toBe(false);
    expect((checkboxes[1].element as HTMLInputElement).checked).toBe(true);
  });

  it("does not render checkboxes outside selection mode", () => {
    const wrapper = mountList({ feed: fakeFeed({ items: [feedItem()] }) });

    expect(wrapper.find('input[type="checkbox"]').exists()).toBe(false);
  });

  it("re-emits toggle-select with the toggled item", async () => {
    const items = [feedItem({ id: "item-1" }), feedItem({ id: "item-2", title: "Second" })];
    const wrapper = mountList({ feed: fakeFeed({ items }), selectable: true });

    await wrapper.findAll('input[type="checkbox"]')[1].setValue(true);

    expect(wrapper.emitted("toggle-select")).toEqual([[items[1]]]);
  });

  it("re-emits hide and unhide actions with their item", async () => {
    const items = [feedItem({ id: "item-1" })];
    const wrapper = mountList({ feed: fakeFeed({ items }), hideable: true });

    await wrapper.get('[aria-label="隐藏文章"]').trigger("click");
    expect(wrapper.emitted("hide")).toEqual([[items[0]]]);

    const hiddenWrapper = mountList({ feed: fakeFeed({ items }), itemActionMode: "hidden", unhideable: true });
    await hiddenWrapper.get('[aria-label="恢复显示"]').trigger("click");
    expect(hiddenWrapper.emitted("unhide")).toEqual([[items[0]]]);
  });

  it("offers a load-more fallback and triggers the next page", async () => {
    const feed = fakeFeed({ items: [feedItem()], hasNext: true });
    const wrapper = mountList({ feed });

    const loadMore = wrapper.findAll("button").find((button) => button.text().includes("加载更多"));
    expect(loadMore).toBeDefined();

    await loadMore!.trigger("click");
    expect(feed.fetchNextPage).toHaveBeenCalledTimes(1);
  });
});

function mountList(options: {
  feed: LinkFeedItems;
  emptyText?: string;
  selectable?: boolean;
  selectedIds?: string[];
  hideable?: boolean;
  unhideable?: boolean;
  itemActionMode?: "all" | "favorite-only" | "hidden";
}) {
  return mount(LinkFeedItemList, {
    props: {
      feed: options.feed,
      sourceName: () => "Example source",
      emptyText: options.emptyText || "暂无数据",
      selectable: options.selectable,
      selectedIds: options.selectedIds,
      hideable: options.hideable,
      unhideable: options.unhideable,
      itemActionMode: options.itemActionMode,
    },
    global: {
      plugins: [[VueQueryPlugin, { queryClient: createFeedTestQueryClient() }]],
    },
  });
}

function fakeFeed(
  options: {
    items?: LinkFeedItem[];
    hasNext?: boolean;
    isLoading?: boolean;
    isLoadingMore?: boolean;
  } = {},
): LinkFeedItems {
  const items = shallowRef(options.items || []);
  return {
    items: computed(() => items.value),
    selectedLinkName: shallowRef(""),
    selectedReadStatus: shallowRef(""),
    hasNext: computed(() => options.hasNext ?? false),
    isLoading: computed(() => options.isLoading ?? false),
    isFetching: computed(() => options.isLoading ?? false),
    isLoadingMore: computed(() => options.isLoadingMore ?? false),
    reload: rstest.fn(),
    fetchNextPage: rstest.fn(),
    selectLink: rstest.fn(),
    selectReadStatus: rstest.fn(),
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
