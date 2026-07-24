import type { Link } from "@/api/generated";
import { describe, expect, it } from "@rstest/core";
import { aggregateLinkFeedStatusMeta, classifyLinkFeedStatus, linkFeedStatusMeta } from "./link-feed-status";

describe("linkFeedStatusMeta", () => {
  it("classifies successful links", () => {
    const meta = linkFeedStatusMeta(
      link({
        lastSuccessAt: "2026-05-25T10:00:00Z",
        feeds: [
          {
            url: "https://example.com/rss.xml",
            lastSuccessAt: "2026-05-25T10:00:00Z",
          },
        ],
      }),
    );

    expect(meta.state).toBe("success");
    expect(meta.tone).toBe("success");
  });

  it("classifies partial failures", () => {
    expect(
      classifyLinkFeedStatus(
        link({
          feeds: [
            {
              url: "https://example.com/rss.xml",
              lastSuccessAt: "2026-05-25T10:00:00Z",
            },
            {
              url: "https://example.com/comments.xml",
              lastError: "timeout",
            },
          ],
        }),
      ),
    ).toBe("partial");
  });

  it("classifies waiting links without refresh state", () => {
    expect(classifyLinkFeedStatus(link())).toBe("waiting");
  });
});

describe("aggregateLinkFeedStatusMeta", () => {
  it("summarizes warning and waiting states", () => {
    const meta = aggregateLinkFeedStatusMeta([
      link({ feeds: [{ url: "https://a.example/rss.xml", lastError: "timeout" }] }),
      link({ lastSuccessAt: "2026-05-25T10:00:00Z" }),
      link(),
    ]);

    expect(meta.tone).toBe("warning");
    expect(meta.label).toBe("1 个异常");
  });

  it("summarizes all-success states", () => {
    expect(aggregateLinkFeedStatusMeta([link({ lastSuccessAt: "2026-05-25T10:00:00Z" })]).state).toBe("success");
  });
});

function link(rss: NonNullable<NonNullable<Link["status"]>["rss"]> = {}): Link {
  return {
    metadata: {
      name: "link-a",
    },
    spec: {
      displayName: "Link A",
      rss: {
        enabled: true,
        feedUrls: ["https://example.com/rss.xml"],
      },
    },
    status: {
      rss,
    },
  } as Link;
}
