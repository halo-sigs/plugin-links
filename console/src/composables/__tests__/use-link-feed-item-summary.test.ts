import { flushPromises } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { runWithFeedTestApp } from "../link-feed-test-utils";
import { useLinkFeedItemSummary } from "../use-link-feed-item-summary";

const apiMocks = vi.hoisted(() => ({
  getLinkFeedItemSummary: vi.fn(),
}));

vi.mock("@/api", () => ({
  linksConsoleApiClient: {
    feed: apiMocks,
  },
}));

describe("useLinkFeedItemSummary", () => {
  beforeEach(() => {
    apiMocks.getLinkFeedItemSummary.mockReset();
  });

  it("returns all saved and hidden counts, including zero", async () => {
    apiMocks.getLinkFeedItemSummary.mockResolvedValue({
      data: { hiddenCount: 0, favoriteCount: 2, readLaterCount: 0 },
    });

    const { result } = runWithFeedTestApp(() => useLinkFeedItemSummary());
    await flushPromises();

    expect(apiMocks.getLinkFeedItemSummary).toHaveBeenCalledTimes(1);
    expect(result.data.value).toEqual({ hiddenCount: 0, favoriteCount: 2, readLaterCount: 0 });
  });

  it("leaves data undefined while pending and after an error", async () => {
    let reject!: (reason?: unknown) => void;
    const pending = new Promise((_, promiseReject) => {
      reject = promiseReject;
    });
    apiMocks.getLinkFeedItemSummary.mockReturnValueOnce(pending);

    const { result } = runWithFeedTestApp(() => useLinkFeedItemSummary());
    await flushPromises();
    expect(result.data.value).toBeUndefined();

    reject(new Error("network"));
    await flushPromises();
    expect(result.data.value).toBeUndefined();
  });

  it("stays idle when disabled", async () => {
    const { result } = runWithFeedTestApp(() => useLinkFeedItemSummary(false));
    await flushPromises();

    expect(apiMocks.getLinkFeedItemSummary).not.toHaveBeenCalled();
    expect(result.data.value).toBeUndefined();
  });
});
