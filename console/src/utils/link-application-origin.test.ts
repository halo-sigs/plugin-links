import type { LinkApplication, Origin } from "@/api/generated";
import { describe, expect, it } from "@rstest/core";
import {
  isCommentRecognitionUnavailable,
  linkApplicationCommentRoute,
  linkApplicationSourceMeta,
  linkApplicationSubjectMeta,
} from "./link-application-origin";

describe("link application source presentation", () => {
  it("labels form, comment, and historical applications", () => {
    expect(linkApplicationSourceMeta(application({ type: "FORM" })).label).toBe("表单申请");
    expect(linkApplicationSourceMeta(application({ type: "COMMENT" })).label).toBe("评论识别");
    expect(linkApplicationSourceMeta(application()).label).toBe("历史申请");
  });

  it("builds supported subject routes and keeps an unknown subject readable", () => {
    expect(
      linkApplicationSubjectMeta({
        group: "content.halo.run",
        kind: "Post",
        name: "post-a",
      }),
    ).toEqual({
      label: "文章 · post-a",
      route: {
        name: "PostEditor",
        query: {
          name: "post-a",
        },
      },
    });

    expect(
      linkApplicationSubjectMeta({
        group: "example.test",
        kind: "ExternalSubject",
        name: "subject-a",
      }),
    ).toEqual({
      label: "ExternalSubject · subject-a",
    });
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

function application(origin?: Origin): LinkApplication {
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
