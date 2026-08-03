import type { LinkApplication, Origin } from "@/api/generated";
import { describe, expect, it } from "@rstest/core";
import {
  isCommentRecognitionUnavailable,
  linkApplicationCommentRoute,
  linkApplicationSourceMeta,
  linkApplicationSubjectMeta,
} from "../link-application-origin";

describe("link application source presentation", () => {
  it("labels form and comment applications", () => {
    expect(linkApplicationSourceMeta(application({ type: "FORM" })).label).toBe("表单申请");
    expect(linkApplicationSourceMeta(application({ type: "COMMENT" })).label).toBe("评论识别");
  });

  it("uses the resolved subject display and provider URL", () => {
    expect(
      linkApplicationSubjectMeta({
        kindName: "文章",
        title: "公开文章",
        url: "https://example.com/posts/public-post",
      }),
    ).toEqual({
      label: "文章 · 公开文章",
      url: "https://example.com/posts/public-post",
    });
  });

  it("uses readable fallbacks without exposing resource names", () => {
    expect(linkApplicationSubjectMeta({ kindName: "文章", title: "  ", url: "/posts/post-a" })).toEqual({
      label: "文章",
      url: "/posts/post-a",
    });
    expect(linkApplicationSubjectMeta({ kindName: "  ", title: "  ", url: "/posts/post-a" })).toEqual({
      label: "评论来源",
      url: "/posts/post-a",
    });
    expect(linkApplicationSubjectMeta({ kindName: "  ", title: "公开文章", url: "/posts/post-a" })).toEqual({
      label: "公开文章",
      url: "/posts/post-a",
    });
    expect(linkApplicationSubjectMeta({ kindName: "文章", title: "  ", url: "/posts/post-a" })?.label).not.toContain(
      "post-a",
    );
  });

  it("returns no display when the backend cannot resolve the subject", () => {
    expect(linkApplicationSubjectMeta(undefined)).toBeUndefined();
  });

  it("builds a comment route from the nested source reference", () => {
    const withComment = application({
      type: "COMMENT",
      comment: {
        name: "comment-a",
      },
    });
    const withoutComment = application({ type: "COMMENT" });

    expect(linkApplicationCommentRoute(withComment)).toEqual({
      name: "Comments",
    });
    expect(linkApplicationCommentRoute(withoutComment)).toBeUndefined();
  });
});

describe("comment recognition warning", () => {
  it("shows only when recognition is configured but not operational", () => {
    expect(
      isCommentRecognitionUnavailable({
        commentApplicationRecognitionEnabled: true,
        commentApplicationRecognitionOperational: false,
      }),
    ).toBe(true);
    expect(
      isCommentRecognitionUnavailable({
        commentApplicationRecognitionEnabled: true,
        commentApplicationRecognitionOperational: true,
      }),
    ).toBe(false);
    expect(isCommentRecognitionUnavailable({})).toBe(false);
  });
});

function application(origin: Origin): LinkApplication {
  return {
    apiVersion: "core.halo.run/v1alpha1",
    kind: "LinkApplication",
    metadata: {
      name: "application-a",
    },
    spec: {
      displayName: "Example",
      origin,
      status: "PENDING",
      url: "https://example.com",
    },
  } as LinkApplication;
}
