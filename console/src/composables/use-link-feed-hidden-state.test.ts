import { beforeEach, describe, expect, it, rstest } from "@rstest/core";
import { flushPromises } from "@vue/test-utils";
import { createFeedTestQueryClient, runWithFeedTestApp } from "./link-feed-test-utils";
import { QK_LINK_FEED_HIDDEN_ITEMS, QK_LINK_FEED_ITEMS } from "./use-link-feed";
import {
  QK_LINK_FEED_HIDDEN_COUNT,
  useLinkFeedHiddenCount,
  useLinkFeedHiddenState,
} from "./use-link-feed-hidden-state";
import { QK_LINK_FEED_UNREAD_SUMMARY } from "./use-link-feed-unread-summary";

const apiMocks = rstest.hoisted(() => ({
  getLinkFeedHiddenCount: rstest.fn(),
  updateLinkFeedItemsHiddenState: rstest.fn(),
}));

rstest.mock("@/api", () => ({
  linksConsoleApiClient: {
    feed: {
      getLinkFeedHiddenCount: apiMocks.getLinkFeedHiddenCount,
      updateLinkFeedItemsHiddenState: apiMocks.updateLinkFeedItemsHiddenState,
    },
  },
}));

describe("useLinkFeedHiddenCount", () => {
  it("returns the exact hidden item count", async () => {
    apiMocks.getLinkFeedHiddenCount.mockResolvedValue({ data: { hiddenCount: 7 } });

    const { result } = runWithFeedTestApp(() => useLinkFeedHiddenCount());
    await flushPromises();

    expect(apiMocks.getLinkFeedHiddenCount).toHaveBeenCalledTimes(1);
    expect(result.data.value?.hiddenCount).toBe(7);
  });

  it("stays idle when disabled", async () => {
    const { result } = runWithFeedTestApp(() => useLinkFeedHiddenCount(false));
    await flushPromises();

    expect(apiMocks.getLinkFeedHiddenCount).not.toHaveBeenCalled();
    expect(result.data.value).toBeUndefined();
  });
});

describe("useLinkFeedHiddenState", () => {
  beforeEach(() => {
    apiMocks.updateLinkFeedItemsHiddenState.mockResolvedValue({
      data: { requestedCount: 2, updatedCount: 1 },
    });
  });

  it("submits distinct item ids and returns batch counts", async () => {
    const { result } = runWithFeedTestApp(() => useLinkFeedHiddenState());

    const mutationResult = await result.setHiddenState(["item-1", "item-2", "item-1"], true);

    expect(apiMocks.updateLinkFeedItemsHiddenState).toHaveBeenCalledWith({
      linkFeedHiddenStateRequest: {
        ids: ["item-1", "item-2"],
        hidden: true,
      },
    });
    expect(mutationResult).toEqual({ requestedCount: 2, updatedCount: 1 });
  });

  it("rejects empty id sets without calling the API", async () => {
    const { result } = runWithFeedTestApp(() => useLinkFeedHiddenState());

    const mutationResult = await result.setHiddenState([], false);

    expect(mutationResult).toBeUndefined();
    expect(apiMocks.updateLinkFeedItemsHiddenState).not.toHaveBeenCalled();
  });

  it("invalidates normal items, hidden items, hidden count and unread summary after success", async () => {
    const queryClient = createFeedTestQueryClient();
    const invalidateSpy = rstest.spyOn(queryClient, "invalidateQueries");
    const { result } = runWithFeedTestApp(() => useLinkFeedHiddenState(), queryClient);

    await result.setHiddenState(["item-1"], false);

    const invalidatedRoots = invalidateSpy.mock.calls.map(
      ([options]) => (options as { queryKey?: unknown[] } | undefined)?.queryKey?.[0],
    );
    expect(invalidatedRoots).toContain(QK_LINK_FEED_ITEMS);
    expect(invalidatedRoots).toContain(QK_LINK_FEED_HIDDEN_ITEMS);
    expect(invalidatedRoots).toContain(QK_LINK_FEED_HIDDEN_COUNT);
    expect(invalidatedRoots).toContain(QK_LINK_FEED_UNREAD_SUMMARY);
  });

  it("does not invalidate queries when the request fails", async () => {
    apiMocks.updateLinkFeedItemsHiddenState.mockRejectedValue(new Error("network"));
    const queryClient = createFeedTestQueryClient();
    const invalidateSpy = rstest.spyOn(queryClient, "invalidateQueries");
    const { result } = runWithFeedTestApp(() => useLinkFeedHiddenState(), queryClient);

    const mutationResult = await result.setHiddenState(["item-1"], true);

    expect(mutationResult).toBeUndefined();
    expect(invalidateSpy).not.toHaveBeenCalled();
    expect(result.isUpdating.value).toBe(false);
  });
});
