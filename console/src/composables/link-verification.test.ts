import type { LinkVerificationRequest, LinkVerificationTriggerResult } from "@/api/generated";
import { describe, expect, it } from "@rstest/core";
import {
  linkVerificationResultMessage,
  normalizeLinkVerificationRequest,
  runLinkVerification,
  startLinkVerification,
  type LinkVerificationApi,
} from "./link-verification";
import { QK_GROUPS_WITH_LINKS } from "./use-link-fetch";

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

function createHarness(result: LinkVerificationTriggerResult = { acceptedCount: 2 }) {
  const verifyCalls: { linkVerificationRequest?: LinkVerificationRequest }[] = [];
  const invalidateCalls: unknown[][] = [];
  const successMessages: string[] = [];
  const infoMessages: string[] = [];

  return {
    linkApi: {
      verifyLinks(request: { linkVerificationRequest?: LinkVerificationRequest }) {
        verifyCalls.push(request);
        return Promise.resolve({ data: result });
      },
    },
    queryClient: {
      invalidateQueries(options: { queryKey: unknown[] }) {
        invalidateCalls.push(options.queryKey);
        return Promise.resolve();
      },
    },
    toast: {
      info(message: string) {
        infoMessages.push(message);
      },
      success(message: string) {
        successMessages.push(message);
      },
    },
    infoMessages,
    invalidateCalls,
    successMessages,
    verifyCalls,
  };
}

describe("normalizeLinkVerificationRequest", () => {
  it("uses selected names before group scope", () => {
    expect(normalizeLinkVerificationRequest({ names: [" link-a ", ""], groupName: "group-a" })).toEqual({
      names: ["link-a"],
    });
  });

  it("falls back to group scope and then all links", () => {
    expect(normalizeLinkVerificationRequest({ groupName: " group-a " })).toEqual({ groupName: "group-a" });
    expect(normalizeLinkVerificationRequest()).toEqual({});
  });
});

describe("runLinkVerification", () => {
  it("triggers verification, shows a manual success message, and invalidates links", async () => {
    const harness = createHarness({ acceptedNames: ["link-a", "link-b"] });

    await runLinkVerification({
      linkApi: harness.linkApi,
      queryClient: harness.queryClient,
      request: { names: ["link-a", "link-b"] },
      showSuccess: true,
      toast: harness.toast,
    });

    expect(harness.verifyCalls).toEqual([{ linkVerificationRequest: { names: ["link-a", "link-b"] } }]);
    expect(harness.successMessages).toEqual(["已开始检测 2 个链接"]);
    expect(harness.invalidateCalls).toEqual([[QK_GROUPS_WITH_LINKS]]);
  });

  it("keeps failures quiet and still invalidates links", async () => {
    const verifyCalls: { linkVerificationRequest?: LinkVerificationRequest }[] = [];
    const invalidateCalls: unknown[][] = [];

    await runLinkVerification({
      linkApi: {
        verifyLinks(request: { linkVerificationRequest?: LinkVerificationRequest }) {
          verifyCalls.push(request);
          return Promise.reject(new Error("failed"));
        },
      },
      queryClient: {
        invalidateQueries(options: { queryKey: unknown[] }) {
          invalidateCalls.push(options.queryKey);
          return Promise.resolve();
        },
      },
      showSuccess: true,
      toast: {
        info() {},
        success() {},
      },
    });

    expect(verifyCalls).toEqual([{ linkVerificationRequest: {} }]);
    expect(invalidateCalls).toEqual([[QK_GROUPS_WITH_LINKS]]);
  });
});

describe("startLinkVerification", () => {
  it("starts verification without waiting for it to settle", async () => {
    const deferred = createDeferred<{ data: LinkVerificationTriggerResult }>();
    const verifyCalls: { linkVerificationRequest?: LinkVerificationRequest }[] = [];
    const invalidateCalls: unknown[][] = [];
    const linkApi: LinkVerificationApi = {
      verifyLinks(request) {
        verifyCalls.push(request);
        return deferred.promise;
      },
    };

    const result = startLinkVerification({
      linkApi,
      queryClient: {
        invalidateQueries(options: { queryKey: unknown[] }) {
          invalidateCalls.push(options.queryKey);
          return Promise.resolve();
        },
      },
      request: { names: ["link-a"] },
    });

    expect(result).toBeUndefined();
    expect(verifyCalls).toEqual([{ linkVerificationRequest: { names: ["link-a"] } }]);
    expect(invalidateCalls).toEqual([]);

    deferred.resolve({ data: { acceptedCount: 1 } });
    await deferred.promise;
    await flushPromises();

    expect(invalidateCalls).toEqual([[QK_GROUPS_WITH_LINKS]]);
  });
});

describe("linkVerificationResultMessage", () => {
  it("summarizes accepted, running, and empty results", () => {
    expect(linkVerificationResultMessage({ acceptedCount: 3 })).toBe("已开始检测 3 个链接");
    expect(linkVerificationResultMessage({ alreadyRunningNames: ["link-a"] })).toBe("1 个链接正在检测中");
    expect(linkVerificationResultMessage({ skippedCount: 2 })).toBe("没有可检测的链接");
  });
});
