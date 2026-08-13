import type { LinkFeedItemPage } from "@/api/generated";
import { flushPromises } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import { runWithFeedTestApp } from "../link-feed-test-utils";
import { QK_LINK_FEED_HIDDEN_ITEMS, QK_LINK_FEED_ITEMS, useLinkFeedItems } from "../use-link-feed";

const apiMocks = vi.hoisted(() => ({
  listLinkFeedItems: vi.fn(),
}));

vi.mock("@/api", () => ({
  linksConsoleApiClient: {
    feed: {
      listLinkFeedItems: apiMocks.listLinkFeedItems,
    },
  },
}));

describe("useLinkFeedItems", () => {
  it("requests visible items without a hidden filter by default", async () => {
    apiMocks.listLinkFeedItems.mockResolvedValue({ data: page({ hasNext: false }) });

    runWithFeedTestApp(() => useLinkFeedItems());
    await flushPromises();

    const params = apiMocks.listLinkFeedItems.mock.calls[0][0];
    expect(params.hidden).toBeUndefined();
    expect(params.limit).toBe(30);
    expect("hidden" in params).toBe(false);
  });

  it("requests hidden items through the fixed hidden filter", async () => {
    apiMocks.listLinkFeedItems.mockResolvedValue({ data: page({ hasNext: false }) });

    runWithFeedTestApp(() =>
      useLinkFeedItems({
        fixedFilter: { hidden: true },
        useReadStatusFilter: false,
        useRouteLinkFilter: false,
      }),
    );
    await flushPromises();

    expect(apiMocks.listLinkFeedItems).toHaveBeenCalledWith(
      expect.objectContaining({
        hidden: true,
        read: undefined,
        linkName: undefined,
      }),
    );
  });

  it("passes the next cursor when loading more", async () => {
    apiMocks.listLinkFeedItems.mockResolvedValue({
      data: page({
        hasNext: true,
        nextBeforePublishedAt: "2026-07-01T10:00:00Z",
        nextBeforeId: "item-10",
      }),
    });

    const { result } = runWithFeedTestApp(() => useLinkFeedItems());
    await flushPromises();

    await result.fetchNextPage();

    expect(apiMocks.listLinkFeedItems).toHaveBeenLastCalledWith(
      expect.objectContaining({
        beforePublishedAt: "2026-07-01T10:00:00Z",
        beforeId: "item-10",
      }),
    );
  });

  it("separates visible and hidden queries into different cache roots", async () => {
    apiMocks.listLinkFeedItems.mockResolvedValue({ data: page({ hasNext: false }) });
    const { queryClient } = runWithFeedTestApp(() => {
      useLinkFeedItems();
      useLinkFeedItems({
        fixedFilter: { hidden: true },
        useReadStatusFilter: false,
        useRouteLinkFilter: false,
      });
    });
    await flushPromises();

    expect(queryClient.getQueriesData({ queryKey: [QK_LINK_FEED_ITEMS] })).toHaveLength(1);
    expect(queryClient.getQueriesData({ queryKey: [QK_LINK_FEED_HIDDEN_ITEMS] })).toHaveLength(1);
  });

  it("applies the read status filter only for the primary list", async () => {
    apiMocks.listLinkFeedItems.mockResolvedValue({ data: page({ hasNext: false }) });
    const { result } = runWithFeedTestApp(() => useLinkFeedItems());

    result.selectReadStatus("unread");
    await flushPromises();

    expect(apiMocks.listLinkFeedItems).toHaveBeenLastCalledWith(
      expect.objectContaining({
        read: false,
      }),
    );
  });
});

function page(overrides: Partial<LinkFeedItemPage> = {}): LinkFeedItemPage {
  return {
    items: [
      {
        id: "item-1",
        title: "First item",
        url: "https://example.com/1",
        hidden: false,
      },
    ],
    hasNext: false,
    ...overrides,
  };
}
