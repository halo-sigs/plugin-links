import { describe, expect, it } from "@rstest/core";
import { commentPlainText, htmlToPlainText } from "./comment-content";

describe("htmlToPlainText", () => {
  it("removes html tags and keeps readable block breaks", () => {
    expect(htmlToPlainText("<p>站点：<strong>Halo</strong></p><p>地址：https://halo.run</p>")).toBe(
      "站点：Halo\n地址：https://halo.run",
    );
  });

  it("uses raw comment content before rendered content", () => {
    expect(
      commentPlainText({
        raw: "<p>raw text</p>",
        content: "<p>rendered text</p>",
      }),
    ).toBe("raw text");
  });
});
