import type { LinkFeedItem } from "@/api/generated";
import { beforeEach, describe, expect, it, rstest } from "@rstest/core";
import { createFeedTestQueryClient, runWithFeedTestApp } from "../link-feed-test-utils";
import { QK_LINK_FEED_HIDDEN_ITEMS } from "../use-link-feed";
import { useLinkFeedItemActions } from "../use-link-feed-item-actions";
import { QK_LINK_FEED_ITEM_SUMMARY } from "../use-link-feed-item-summary";

const apiMocks = rstest.hoisted(() => ({
  markLinkFeedItemFavorite: rstest.fn(),
  markLinkFeedItemRead: rstest.fn(),
  markLinkFeedItemReadLater: rstest.fn(),
}));

rstest.mock("@/api", () => ({
  linksConsoleApiClient: {
    feed: apiMocks,
  },
}));

describe("useLinkFeedItemActions hidden item open behavior", () => {
  beforeEach(() => {
    apiMocks.markLinkFeedItemFavorite.mockResolvedValue({});
    apiMocks.markLinkFeedItemRead.mockResolvedValue({});
    apiMocks.markLinkFeedItemReadLater.mockResolvedValue({});
  });

  it("marks an unread hidden item as read without changing favorite state", async () => {
    const item = hiddenItem({ read: false, favorite: true, readLater: false });
    const { result } = runWithFeedTestApp(() => useLinkFeedItemActions(item));

    expect(await result.openItem()).toBe(true);
    expect(apiMocks.markLinkFeedItemRead).toHaveBeenCalledWith({ id: "item-1", read: true });
    expect(apiMocks.markLinkFeedItemFavorite).not.toHaveBeenCalled();
    expect(apiMocks.markLinkFeedItemReadLater).not.toHaveBeenCalled();
  });

  it("removes read-later and refreshes the hidden list when the item is already read", async () => {
    const queryClient = createFeedTestQueryClient();
    const invalidateSpy = rstest.spyOn(queryClient, "invalidateQueries");
    const item = hiddenItem({ read: true, favorite: true, readLater: true });
    const { result } = runWithFeedTestApp(() => useLinkFeedItemActions(item), queryClient);

    expect(await result.openItem()).toBe(true);
    expect(apiMocks.markLinkFeedItemRead).not.toHaveBeenCalled();
    expect(apiMocks.markLinkFeedItemReadLater).toHaveBeenCalledWith({ id: "item-1", readLater: false });
    expect(apiMocks.markLinkFeedItemFavorite).not.toHaveBeenCalled();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: [QK_LINK_FEED_HIDDEN_ITEMS] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: [QK_LINK_FEED_ITEM_SUMMARY] });
  });

  it("refreshes the item summary after a favorite change", async () => {
    const queryClient = createFeedTestQueryClient();
    const invalidateSpy = rstest.spyOn(queryClient, "invalidateQueries");
    const { result } = runWithFeedTestApp(() => useLinkFeedItemActions(hiddenItem({ hidden: false })), queryClient);

    expect(await result.markFavorite(true)).toBe(true);

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: [QK_LINK_FEED_ITEM_SUMMARY] });
  });

  it("does not report success or invalidate the summary when read-later fails", async () => {
    apiMocks.markLinkFeedItemReadLater.mockRejectedValue(new Error("network"));
    const queryClient = createFeedTestQueryClient();
    const invalidateSpy = rstest.spyOn(queryClient, "invalidateQueries");
    const { result } = runWithFeedTestApp(() => useLinkFeedItemActions(hiddenItem({ hidden: false })), queryClient);

    expect(await result.markReadLater(true)).toBe(false);
    expect(invalidateSpy).not.toHaveBeenCalledWith({ queryKey: [QK_LINK_FEED_ITEM_SUMMARY] });
  });
});

function hiddenItem(overrides: Partial<LinkFeedItem>): LinkFeedItem {
  return {
    id: "item-1",
    title: "Hidden article",
    url: "https://example.com/hidden",
    read: false,
    favorite: false,
    readLater: false,
    hidden: true,
    ...overrides,
  } as LinkFeedItem;
}
