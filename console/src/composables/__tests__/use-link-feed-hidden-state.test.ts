import { beforeEach, describe, expect, it, rstest } from "@rstest/core";
import { createFeedTestQueryClient, runWithFeedTestApp } from "../link-feed-test-utils";
import { QK_LINK_FEED_HIDDEN_ITEMS, QK_LINK_FEED_ITEMS } from "../use-link-feed";
import { useLinkFeedHiddenState } from "../use-link-feed-hidden-state";
import { QK_LINK_FEED_ITEM_SUMMARY } from "../use-link-feed-item-summary";
import { QK_LINK_FEED_UNREAD_SUMMARY } from "../use-link-feed-unread-summary";

const apiMocks = rstest.hoisted(() => ({
  updateLinkFeedItemsHiddenState: rstest.fn(),
}));

rstest.mock("@/api", () => ({
  linksConsoleApiClient: {
    feed: {
      updateLinkFeedItemsHiddenState: apiMocks.updateLinkFeedItemsHiddenState,
    },
  },
}));

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

  it("invalidates normal items, hidden items, item summary and unread summary after success", async () => {
    const queryClient = createFeedTestQueryClient();
    const invalidateSpy = rstest.spyOn(queryClient, "invalidateQueries");
    const { result } = runWithFeedTestApp(() => useLinkFeedHiddenState(), queryClient);

    await result.setHiddenState(["item-1"], false);

    const invalidatedRoots = invalidateSpy.mock.calls.map(
      ([options]) => (options as { queryKey?: unknown[] } | undefined)?.queryKey?.[0],
    );
    expect(invalidatedRoots).toContain(QK_LINK_FEED_ITEMS);
    expect(invalidatedRoots).toContain(QK_LINK_FEED_HIDDEN_ITEMS);
    expect(invalidatedRoots).toContain(QK_LINK_FEED_ITEM_SUMMARY);
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
