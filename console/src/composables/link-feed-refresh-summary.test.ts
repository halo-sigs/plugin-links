import { describe, expect, it } from "@rstest/core";
import { classifyLinkFeedRefreshResult } from "./link-feed-refresh-summary";

describe("classifyLinkFeedRefreshResult", () => {
  it("treats all successful feed results as success", () => {
    expect(
      classifyLinkFeedRefreshResult({
        feeds: [{}, {}],
      }),
    ).toBe("success");
  });

  it("treats mixed feed results as partial", () => {
    expect(
      classifyLinkFeedRefreshResult({
        feeds: [{}, { error: "timeout" }],
      }),
    ).toBe("partial");
  });

  it("treats all failed feed results as failed", () => {
    expect(
      classifyLinkFeedRefreshResult({
        feeds: [{ error: "blocked" }, { error: "timeout" }],
      }),
    ).toBe("failed");
  });

  it("honors the aggregate partial-failure flag", () => {
    expect(
      classifyLinkFeedRefreshResult({
        partialFailure: true,
      }),
    ).toBe("partial");
  });
});
