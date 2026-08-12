import { describe, expect, it } from "@rstest/core";
import { commentPlainText, htmlToPlainText, plainTextToSafeHtml } from "../comment-content";

describe("htmlToPlainText", () => {
  it("removes html tags and keeps readable block breaks", () => {
    expect(htmlToPlainText("<p>站点：<strong>Halo</strong></p><p>地址：https://halo.run</p>")).toBe(
      "站点：Halo\n地址：https://halo.run",
    );
  });

  it("keeps anchor text without exposing its html markup", () => {
    expect(htmlToPlainText('<p>博客地址：<a target="_blank" href="https://halo.run">https://halo.run</a></p>')).toBe(
      "博客地址：https://halo.run",
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

describe("plainTextToSafeHtml", () => {
  it("escapes HTML-significant characters instead of interpreting them", () => {
    expect(plainTextToSafeHtml('<script>alert("x")</script> & <b>bold</b>')).toBe(
      "&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt; &amp; &lt;b&gt;bold&lt;/b&gt;",
    );
  });

  it("preserves newlines as safe line breaks", () => {
    expect(plainTextToSafeHtml("第一行\n第二行\r\n第三行")).toBe("第一行<br />第二行<br />第三行");
  });

  it("keeps plain text untouched", () => {
    expect(plainTextToSafeHtml("已通过，欢迎交换友链！")).toBe("已通过，欢迎交换友链！");
  });
});
