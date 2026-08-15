import { describe, expect, it } from "vitest";
import { applyLinkCommentExtraction } from "../link-comment-extraction";

describe("applyLinkCommentExtraction", () => {
  it("prefills the link form from a successful extraction", () => {
    const target = {
      url: "",
      displayName: "",
      logo: "",
      description: "",
      rss: {
        enabled: false,
        feedUrls: ["https://example.com/feed.xml"],
      },
    };

    const result = applyLinkCommentExtraction(target, {
      url: "https://example.com",
      displayName: "Example",
      logo: "https://example.com/logo.png",
      description: "An example site",
      rssUrl: "https://example.com/rss.xml",
    });

    expect(target).toEqual({
      url: "https://example.com",
      displayName: "Example",
      logo: "https://example.com/logo.png",
      description: "An example site",
      rss: {
        enabled: true,
        feedUrls: ["https://example.com/feed.xml", "https://example.com/rss.xml"],
      },
    });
    expect(result.warnings).toEqual([]);
  });

  it("ignores malformed URLs and reports local validation warnings", () => {
    const target = {
      url: "",
      displayName: "",
      rss: {
        enabled: false,
        feedUrls: [],
      },
    };

    const result = applyLinkCommentExtraction(target, {
      url: "javascript:alert(1)",
      logo: "not-a-url",
      rssUrl: "ftp://example.com/feed.xml",
    });

    expect(target.url).toBe("");
    expect(target.rss.enabled).toBe(false);
    expect(result.warnings).toHaveLength(3);
  });
});
