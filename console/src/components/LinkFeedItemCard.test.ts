import type { LinkFeedItem } from "@/api/generated";
import { describe, expect, it, rstest } from "@rstest/core";
import { VueQueryPlugin } from "@tanstack/vue-query";
import { flushPromises, mount } from "@vue/test-utils";
import { createFeedTestQueryClient } from "../composables/link-feed-test-utils";
import LinkFeedItemCard from "./LinkFeedItemCard.vue";

const actionMocks = rstest.hoisted(() => ({
  openItem: rstest.fn(),
  toggleFavorite: rstest.fn(),
  toggleRead: rstest.fn(),
  toggleReadLater: rstest.fn(),
}));

rstest.mock("@/composables/use-link-feed-item-actions", () => ({
  useLinkFeedItemActions: () => ({
    isMarkingFavorite: { value: false },
    isMarkingRead: { value: false },
    isMarkingReadLater: { value: false },
    openItem: actionMocks.openItem,
    toggleFavorite: actionMocks.toggleFavorite,
    toggleRead: actionMocks.toggleRead,
    toggleReadLater: actionMocks.toggleReadLater,
  }),
}));

describe("LinkFeedItemCard", () => {
  it("renders title, source and read-only state badges", () => {
    const wrapper = mountCard({
      item: feedItem({ read: true, favorite: true, readLater: true }),
    });

    expect(wrapper.text()).toContain("Example article");
    expect(wrapper.text()).toContain("Example source");
    expect(wrapper.text()).toContain("已读");
    expect(wrapper.text()).toContain("已收藏");
    expect(wrapper.text()).toContain("稍后阅读");
  });

  it("emits hide from the per-item hide action", async () => {
    const wrapper = mountCard({ hideable: true });

    await wrapper.get('[aria-label="隐藏文章"]').trigger("click");

    expect(wrapper.emitted("hide")).toHaveLength(1);
  });

  it("hides the per-item hide action unless hideable", () => {
    const wrapper = mountCard();

    expect(wrapper.find('[aria-label="隐藏文章"]').exists()).toBe(false);
  });

  it("keeps hidden mode free of state toggles and offers direct unhide", async () => {
    const wrapper = mountCard({
      itemActionMode: "hidden",
      unhideable: true,
      item: feedItem({ read: true, favorite: true, readLater: true }),
    });

    expect(wrapper.find('[aria-label="收藏"]').exists()).toBe(false);
    expect(wrapper.find('[aria-label="取消收藏"]').exists()).toBe(false);
    expect(wrapper.find('[aria-label="稍后阅读"]').exists()).toBe(false);
    expect(wrapper.find('[aria-label="移出稍后阅读"]').exists()).toBe(false);
    expect(wrapper.find('[aria-label="标为已读"]').exists()).toBe(false);
    expect(wrapper.find('[aria-label="标为未读"]').exists()).toBe(false);
    expect(wrapper.text()).toContain("已收藏");
    expect(wrapper.text()).toContain("稍后阅读");

    await wrapper.get('[aria-label="恢复显示"]').trigger("click");

    expect(wrapper.emitted("unhide")).toHaveLength(1);
  });

  it("opens the external article through the shared open behavior in hidden mode", async () => {
    const wrapper = mountCard({ itemActionMode: "hidden" });

    await wrapper.get("a.feed-item__title--link").trigger("click");
    await flushPromises();

    expect(actionMocks.openItem).toHaveBeenCalledTimes(1);
  });

  it("shows a selection checkbox and emits toggle-select", async () => {
    const wrapper = mountCard({ selectable: true, selected: false });
    const checkbox = wrapper.get('input[type="checkbox"]');

    expect(checkbox.attributes("aria-label")).toContain("选择文章");
    expect((checkbox.element as HTMLInputElement).checked).toBe(false);

    await checkbox.setValue(true);

    expect(wrapper.emitted("toggle-select")).toHaveLength(1);
  });

  it("reflects the selected state on the checkbox", () => {
    const wrapper = mountCard({ selectable: true, selected: true });

    expect((wrapper.get('input[type="checkbox"]').element as HTMLInputElement).checked).toBe(true);
  });
});

function mountCard(
  options: {
    item?: LinkFeedItem;
    itemActionMode?: "all" | "favorite-only" | "hidden";
    selectable?: boolean;
    selected?: boolean;
    hideable?: boolean;
    unhideable?: boolean;
  } = {},
) {
  return mount(LinkFeedItemCard, {
    props: {
      item: options.item || feedItem(),
      sourceName: "Example source",
      itemActionMode: options.itemActionMode,
      selectable: options.selectable,
      selected: options.selected,
      hideable: options.hideable,
      unhideable: options.unhideable,
    },
    global: {
      plugins: [[VueQueryPlugin, { queryClient: createFeedTestQueryClient() }]],
    },
  });
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
