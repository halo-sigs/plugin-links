import type { LinkFeedItem } from "@/api/generated";
import type { LinkFeedItems } from "@/composables/use-link-feed";
import { Dialog, Toast } from "@halo-dev/components";
import { describe, expect, it, rstest } from "@rstest/core";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { computed, nextTick, shallowRef } from "vue";
import LinkFeedHiddenItemsModal from "./LinkFeedHiddenItemsModal.vue";

const hiddenStateMocks = rstest.hoisted(() => ({
  setHiddenState: rstest.fn(),
}));

rstest.mock("@/composables/use-link-feed-hidden-state", () => ({
  useLinkFeedHiddenState: () => ({
    isUpdating: shallowRef(false),
    setHiddenState: hiddenStateMocks.setHiddenState,
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

describe("LinkFeedHiddenItemsModal", () => {
  it("unhides a single item directly without confirmation", async () => {
    hiddenStateMocks.setHiddenState.mockResolvedValue({ requestedCount: 1, updatedCount: 1 });
    const dialogSpy = rstest.spyOn(Dialog, "warning");
    const toastSpy = rstest.spyOn(Toast, "success");
    mountModal(fakeFeed({ items: [feedItem({ id: "item-1" })] }));
    await nextTick();

    modalButton("恢复显示")?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await flushPromises();

    expect(dialogSpy).not.toHaveBeenCalled();
    expect(hiddenStateMocks.setHiddenState).toHaveBeenCalledWith(["item-1"], false);
    expect(toastSpy).toHaveBeenCalledWith("已恢复 1 篇文章");
  });

  it("selects all loaded items only", async () => {
    const feed = fakeFeed({
      items: [feedItem({ id: "item-1" }), feedItem({ id: "item-2", title: "Second" })],
      hasNext: true,
    });
    mountModal(feed);
    await nextTick();

    modalButton("全选已加载")?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await nextTick();

    const checkboxes = Array.from(modalRoot().querySelectorAll<HTMLInputElement>('input[type="checkbox"]'));
    expect(checkboxes).toHaveLength(2);
    expect(checkboxes.every((checkbox) => checkbox.checked)).toBe(true);
    expect(modalRoot().textContent).toContain("已选 2 篇");
  });

  it("confirms batch unhide with the selected scope and clears the selection", async () => {
    hiddenStateMocks.setHiddenState.mockResolvedValue({ requestedCount: 2, updatedCount: 2 });
    const dialogSpy = rstest.spyOn(Dialog, "warning");
    const toastSpy = rstest.spyOn(Toast, "success");
    const feed = fakeFeed({
      items: [feedItem({ id: "item-1" }), feedItem({ id: "item-2", title: "Second" })],
    });
    mountModal(feed);
    await nextTick();

    modalButton("全选已加载")?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await nextTick();
    modalButton("恢复所选")?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await nextTick();

    expect(dialogSpy).toHaveBeenCalledTimes(1);
    const dialogOptions = dialogSpy.mock.calls[0][0] as { description?: string; onConfirm?: () => Promise<void> };
    expect(dialogOptions.description).toContain("2 篇已隐藏文章");
    expect(hiddenStateMocks.setHiddenState).not.toHaveBeenCalled();

    await dialogOptions.onConfirm?.();
    await flushPromises();

    expect(hiddenStateMocks.setHiddenState).toHaveBeenCalledWith(["item-1", "item-2"], false);
    expect(toastSpy).toHaveBeenCalledWith("已恢复 2 篇文章");
    expect(modalRoot().textContent).toContain("勾选文章后可批量恢复");
    expect(modalRoot().textContent).not.toContain("已选 2 篇");
  });

  it("keeps the selection when the batch unhide request fails", async () => {
    hiddenStateMocks.setHiddenState.mockResolvedValue(undefined);
    const dialogSpy = rstest.spyOn(Dialog, "warning");
    mountModal(fakeFeed({ items: [feedItem({ id: "item-1" })] }));
    await nextTick();

    modalButton("全选已加载")?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await nextTick();
    modalButton("恢复所选")?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await nextTick();
    const dialogOptions = dialogSpy.mock.calls[0][0] as { onConfirm?: () => Promise<void> };
    await dialogOptions.onConfirm?.();
    await flushPromises();

    expect(modalRoot().textContent).toContain("已选 1 篇");
  });

  it("clears the selection when the list is reloaded", async () => {
    const feed = fakeFeed({ items: [feedItem({ id: "item-1" })] });
    mountModal(feed);
    await nextTick();

    modalButton("全选已加载")?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await nextTick();
    expect(modalRoot().textContent).toContain("已选 1 篇");

    modalButton("取消选择")?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await nextTick();
    expect(modalRoot().textContent).not.toContain("已选 1 篇");

    modalButton("全选已加载")?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await nextTick();
    modalButton("重新加载")?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    await flushPromises();

    expect(feed.reload).toHaveBeenCalledTimes(1);
    expect(modalRoot().textContent).not.toContain("已选 1 篇");
  });

  it("disables batch actions when nothing is selected", async () => {
    mountModal(fakeFeed({ items: [feedItem({ id: "item-1" })] }));
    await nextTick();

    expect((modalButton("恢复所选") as HTMLButtonElement).disabled).toBe(true);
    expect((modalButton("取消选择") as HTMLButtonElement).disabled).toBe(true);
  });
});

let mountedWrapper: VueWrapper | undefined;

function mountModal(feed: LinkFeedItems) {
  mountedWrapper?.unmount();
  document.body.innerHTML = "";
  const host = document.createElement("div");
  document.body.appendChild(host);
  mountedWrapper = mount(LinkFeedHiddenItemsModal, {
    props: {
      feed,
      sourceName: () => "Example source",
    },
    attachTo: host,
  });
  return mountedWrapper;
}

function modalRoot(): HTMLElement {
  return (document.body.querySelector(".modal-wrapper") as HTMLElement) || document.body;
}

function modalButton(text: string): HTMLButtonElement | undefined {
  const candidates = Array.from(modalRoot().querySelectorAll<HTMLButtonElement>("button, [role='button']"));
  const match = candidates.find(
    (element) => element.textContent?.includes(text) && !element.getAttribute("aria-label"),
  );
  if (match) {
    return match;
  }
  const ariaMatch = Array.from(modalRoot().querySelectorAll<HTMLElement>("[aria-label]")).find((element) =>
    element.getAttribute("aria-label")?.includes(text),
  );
  return ariaMatch as HTMLButtonElement | undefined;
}

function fakeFeed(
  options: {
    items?: LinkFeedItem[];
    hasNext?: boolean;
    isLoading?: boolean;
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
    isLoadingMore: computed(() => false),
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
    hidden: true,
    ...overrides,
  } as LinkFeedItem;
}
