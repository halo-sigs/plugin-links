import type { LinkApplication } from "@/api/generated";
import { describe, expect, it, rstest } from "@rstest/core";
import { runWithFeedTestApp } from "../link-feed-test-utils";
import {
  approveLinkThenHandleOriginComment,
  runOriginCommentActions,
  useOriginCommentActions,
} from "../use-origin-comment-actions";

const apiMocks = rstest.hoisted(() => ({
  patchComment: rstest.fn(),
  createReply: rstest.fn(),
  listReplies: rstest.fn(),
}));

rstest.mock("@halo-dev/api-client", () => ({
  axiosInstance: {},
  coreApiClient: {
    content: {
      comment: {
        patchComment: apiMocks.patchComment,
      },
    },
  },
  consoleApiClient: {
    content: {
      comment: {
        createReply: apiMocks.createReply,
      },
      reply: {
        listReplies: apiMocks.listReplies,
      },
    },
  },
}));

const approvedOriginComment = { approved: true, hidden: false };
const unapprovedOriginComment = { approved: false, hidden: false };

describe("runOriginCommentActions approval", () => {
  it("patches approval state and time for an unapproved comment", async () => {
    apiMocks.patchComment.mockResolvedValue({ data: {} });

    const outcome = await runOriginCommentActions({
      commentName: "comment-a",
      intent: { approve: true, replyText: "" },
      refetchOriginComment: async () => unapprovedOriginComment,
    });

    expect(outcome).toEqual({ type: "completed", approvedComment: true, replied: false });
    expect(apiMocks.patchComment).toHaveBeenCalledTimes(1);
    const request = apiMocks.patchComment.mock.calls[0][0];
    expect(request.name).toBe("comment-a");
    expect(request.jsonPatchInner).toHaveLength(2);
    expect(request.jsonPatchInner[0]).toEqual({ op: "add", path: "/spec/approved", value: true });
    expect(request.jsonPatchInner[1].path).toBe("/spec/approvedTime");
    expect(Number.isNaN(Date.parse(request.jsonPatchInner[1].value))).toBe(false);
    expect(apiMocks.createReply).not.toHaveBeenCalled();
  });

  it("skips the approval patch when the refreshed state is already approved", async () => {
    const outcome = await runOriginCommentActions({
      commentName: "comment-a",
      intent: { approve: true, replyText: "" },
      refetchOriginComment: async () => approvedOriginComment,
    });

    expect(outcome).toEqual({ type: "completed", approvedComment: true, replied: false });
    expect(apiMocks.patchComment).not.toHaveBeenCalled();
  });

  it("reports a determinate approval failure", async () => {
    apiMocks.patchComment.mockRejectedValue({ isAxiosError: true, response: { status: 403 } });

    const outcome = await runOriginCommentActions({
      commentName: "comment-a",
      intent: { approve: true, replyText: "" },
      refetchOriginComment: async () => unapprovedOriginComment,
    });

    expect(outcome).toEqual({ type: "failed", action: "approve", status: 403 });
  });

  it("confirms the actual state instead of claiming failure when the approval response is lost", async () => {
    apiMocks.patchComment.mockRejectedValue({ isAxiosError: true, response: undefined });
    const refetchOriginComment = rstest
      .fn()
      .mockResolvedValueOnce(unapprovedOriginComment)
      .mockResolvedValueOnce(approvedOriginComment);

    const outcome = await runOriginCommentActions({
      commentName: "comment-a",
      intent: { approve: true, replyText: "" },
      refetchOriginComment,
    });

    expect(outcome).toEqual({ type: "completed", approvedComment: true, replied: false });
    expect(apiMocks.patchComment).toHaveBeenCalledTimes(1);
    expect(refetchOriginComment).toHaveBeenCalledTimes(2);
  });

  it("reports approval failure only when a lost response did not take effect", async () => {
    apiMocks.patchComment.mockRejectedValue({ isAxiosError: true, response: { status: 500 } });

    const outcome = await runOriginCommentActions({
      commentName: "comment-a",
      intent: { approve: true, replyText: "" },
      refetchOriginComment: async () => unapprovedOriginComment,
    });

    expect(outcome).toEqual({ type: "failed", action: "approve", status: 500 });
  });

  it("reports an indeterminate approval when the confirming refresh also fails", async () => {
    apiMocks.patchComment.mockRejectedValue({ isAxiosError: true, response: { status: 500 } });
    const refetchOriginComment = rstest.fn().mockRejectedValue(new Error("refresh failed"));

    const outcome = await runOriginCommentActions({
      commentName: "comment-a",
      intent: { approve: true, replyText: "" },
      refetchOriginComment,
    });

    expect(outcome).toEqual({ type: "indeterminate", action: "approve" });
  });
});

describe("runOriginCommentActions reply", () => {
  it("creates one escaped reply without a preceding approval patch", async () => {
    apiMocks.createReply.mockResolvedValue({ data: {} });

    const outcome = await runOriginCommentActions({
      commentName: "comment-a",
      intent: { approve: true, replyText: '已通过\n<b>欢迎</b> & "交换友链"' },
      refetchOriginComment: async () => unapprovedOriginComment,
    });

    expect(outcome).toEqual({ type: "completed", approvedComment: false, replied: true });
    expect(apiMocks.patchComment).not.toHaveBeenCalled();
    expect(apiMocks.createReply).toHaveBeenCalledTimes(1);
    expect(apiMocks.createReply).toHaveBeenCalledWith({
      name: "comment-a",
      replyRequest: {
        raw: '已通过\n<b>欢迎</b> & "交换友链"',
        content: "已通过<br />&lt;b&gt;欢迎&lt;/b&gt; &amp; &quot;交换友链&quot;",
        allowNotification: true,
        quoteReply: undefined,
      },
    });
  });

  it("reports a determinate reply failure for 4xx responses", async () => {
    apiMocks.createReply.mockRejectedValue({ isAxiosError: true, response: { status: 403 } });

    const outcome = await runOriginCommentActions({
      commentName: "comment-a",
      intent: { approve: false, replyText: "你好" },
      refetchOriginComment: async () => unapprovedOriginComment,
    });

    expect(outcome).toEqual({ type: "failed", action: "reply", status: 403 });
    expect(apiMocks.listReplies).not.toHaveBeenCalled();
  });

  it("treats a 5xx reply response as indeterminate and refreshes state instead of failing", async () => {
    apiMocks.createReply.mockRejectedValue({ isAxiosError: true, response: { status: 500 } });
    apiMocks.listReplies.mockResolvedValue({ data: { items: [{}] } });

    const outcome = await runOriginCommentActions({
      commentName: "comment-a",
      intent: { approve: false, replyText: "你好" },
      refetchOriginComment: async () => unapprovedOriginComment,
    });

    expect(outcome).toEqual({ type: "indeterminate", action: "reply", commentApproved: false, recentReplyCount: 1 });
    expect(apiMocks.createReply).toHaveBeenCalledTimes(1);
    expect(apiMocks.listReplies).toHaveBeenCalledTimes(1);
  });

  it("never resubmits an indeterminate reply and refreshes current state instead", async () => {
    apiMocks.createReply.mockRejectedValue({ isAxiosError: true, response: undefined });
    apiMocks.listReplies.mockResolvedValue({ data: { items: [{}, {}] } });
    const refetchOriginComment = rstest.fn().mockResolvedValue(approvedOriginComment);

    const outcome = await runOriginCommentActions({
      commentName: "comment-a",
      intent: { approve: false, replyText: "你好" },
      refetchOriginComment,
    });

    expect(outcome).toEqual({ type: "indeterminate", action: "reply", commentApproved: true, recentReplyCount: 2 });
    expect(apiMocks.createReply).toHaveBeenCalledTimes(1);
    expect(refetchOriginComment).toHaveBeenCalledTimes(1);
    expect(apiMocks.listReplies).toHaveBeenCalledWith({
      commentName: "comment-a",
      page: 1,
      size: 20,
      sort: ["metadata.creationTimestamp,desc"],
    });
  });

  it("still reports indeterminate when the state refresh fails", async () => {
    apiMocks.createReply.mockRejectedValue({ isAxiosError: true, response: undefined });
    apiMocks.listReplies.mockRejectedValue({ isAxiosError: true, response: undefined });

    const outcome = await runOriginCommentActions({
      commentName: "comment-a",
      intent: { approve: false, replyText: "你好" },
      refetchOriginComment: async () => {
        throw new Error("gone");
      },
    });

    expect(outcome).toEqual({
      type: "indeterminate",
      action: "reply",
      commentApproved: undefined,
      recentReplyCount: undefined,
    });
    expect(apiMocks.createReply).toHaveBeenCalledTimes(1);
  });
});

describe("useOriginCommentActions", () => {
  it("completes without touching comment APIs when no action is selected (e.g. form origin)", async () => {
    const formApplication = {
      metadata: { name: "app-form" },
      spec: { origin: { type: "FORM" } },
    } as LinkApplication;
    const { result } = runWithFeedTestApp(() =>
      useOriginCommentActions({
        application: formApplication,
        refetchOriginComment: async () => undefined,
      }),
    );

    const outcome = await result.handleOriginComment({ approve: false, replyText: "" });

    expect(outcome).toEqual({ type: "completed", approvedComment: false, replied: false });
    expect(apiMocks.patchComment).not.toHaveBeenCalled();
    expect(apiMocks.createReply).not.toHaveBeenCalled();
  });
});

describe("approveLinkThenHandleOriginComment", () => {
  it("prevents every comment request when link approval fails", async () => {
    const handleComment = rstest.fn();

    const result = await approveLinkThenHandleOriginComment({
      approveLink: async () => {
        throw new Error("validation failed");
      },
      handleComment,
    });

    expect(result).toEqual({ linkApproved: false });
    expect(handleComment).not.toHaveBeenCalled();
    expect(apiMocks.patchComment).not.toHaveBeenCalled();
    expect(apiMocks.createReply).not.toHaveBeenCalled();
  });

  it("runs comment handling only after link approval succeeds", async () => {
    const order: string[] = [];

    const result = await approveLinkThenHandleOriginComment({
      approveLink: async () => {
        order.push("link");
      },
      handleComment: async () => {
        order.push("comment");
        return { type: "completed", approvedComment: true, replied: false };
      },
    });

    expect(order).toEqual(["link", "comment"]);
    expect(result).toEqual({
      linkApproved: true,
      commentOutcome: { type: "completed", approvedComment: true, replied: false },
    });
  });
});
