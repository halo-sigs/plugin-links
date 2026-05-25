import { describe, expect, it } from "@rstest/core";
import { refreshInitialLinkFeed, startInitialLinkFeedRefresh } from "./link-feed-initial-refresh";
import { QK_LINK_FEED_ITEMS } from "./use-link-feed";
import { QK_GROUPS_WITH_LINKS, QK_RSS_GROUPS_WITH_LINKS } from "./use-link-fetch";

function createDeferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });
  return { promise, reject, resolve };
}

async function flushPromises() {
  await Promise.resolve();
  await Promise.resolve();
}

function createRefreshHarness() {
  const refreshCalls: { name: string }[] = [];
  const invalidateCalls: unknown[][] = [];
  const successMessages: string[] = [];
  const deferred = createDeferred<unknown>();

  return {
    deferred,
    feedApi: {
      refreshLinkFeed(request: { name: string }) {
        refreshCalls.push(request);
        return deferred.promise;
      },
    },
    invalidateCalls,
    queryClient: {
      invalidateQueries(options: { queryKey: unknown[] }) {
        invalidateCalls.push(options.queryKey);
        return Promise.resolve();
      },
    },
    refreshCalls,
    successMessages,
    toast: {
      success(message: string) {
        successMessages.push(message);
      },
    },
  };
}

describe("startInitialLinkFeedRefresh", () => {
  it("starts the refresh without waiting for it to settle", async () => {
    const harness = createRefreshHarness();

    const result = startInitialLinkFeedRefresh({
      feedApi: harness.feedApi,
      linkName: "link-a",
      queryClient: harness.queryClient,
      toast: harness.toast,
    });

    expect(result).toBeUndefined();
    expect(harness.refreshCalls).toEqual([{ name: "link-a" }]);
    expect(harness.successMessages).toEqual([]);
    expect(harness.invalidateCalls).toEqual([]);

    harness.deferred.resolve({});
    await harness.deferred.promise;
    await flushPromises();

    expect(harness.successMessages).toEqual(["RSS 已自动获取"]);
    expect(harness.invalidateCalls).toEqual([[QK_GROUPS_WITH_LINKS], [QK_RSS_GROUPS_WITH_LINKS], [QK_LINK_FEED_ITEMS]]);
  });

  it("does nothing without a link name", () => {
    const harness = createRefreshHarness();

    startInitialLinkFeedRefresh({
      feedApi: harness.feedApi,
      queryClient: harness.queryClient,
      toast: harness.toast,
    });

    expect(harness.refreshCalls).toEqual([]);
    expect(harness.invalidateCalls).toEqual([]);
  });
});

describe("refreshInitialLinkFeed", () => {
  it("invalidates RSS queries even when the refresh fails", async () => {
    const harness = createRefreshHarness();

    const refreshPromise = refreshInitialLinkFeed({
      feedApi: harness.feedApi,
      linkName: "link-a",
      queryClient: harness.queryClient,
      toast: harness.toast,
    });
    harness.deferred.reject(new Error("timeout"));

    await refreshPromise;

    expect(harness.successMessages).toEqual([]);
    expect(harness.invalidateCalls).toEqual([[QK_GROUPS_WITH_LINKS], [QK_RSS_GROUPS_WITH_LINKS], [QK_LINK_FEED_ITEMS]]);
  });
});
