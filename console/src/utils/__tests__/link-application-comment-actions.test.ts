import { describe, expect, it } from "vitest";
import {
  isIndeterminateCommentRequestError,
  linkApplicationActionSummary,
  linkApplicationCommentActionsView,
  normalizeOriginCommentActionIntent,
  originCommentActionFailureAlert,
  originCommentActionIntentEmpty,
  originCommentActionSuccessMessage,
  originCommentOutcomeRequiresReplyConfirmation,
} from "../link-application-comment-actions";

describe("linkApplicationCommentActionsView", () => {
  it("hides comment actions for form-origin applications", () => {
    const view = linkApplicationCommentActionsView({
      originType: "FORM",
      applicationStatus: "PENDING",
      commentState: "ready",
      approved: false,
    });
    expect(view.visible).toBe(false);
  });

  it("hides comment actions for rejected applications", () => {
    const view = linkApplicationCommentActionsView({
      originType: "COMMENT",
      applicationStatus: "REJECTED",
      commentState: "ready",
      approved: false,
    });
    expect(view.visible).toBe(false);
  });

  it("offers approval selected by default for an unapproved source comment", () => {
    const view = linkApplicationCommentActionsView({
      originType: "COMMENT",
      applicationStatus: "PENDING",
      commentState: "ready",
      approved: false,
      hidden: false,
    });
    expect(view.visible).toBe(true);
    expect(view.standalone).toBe(false);
    expect(view.controlsEnabled).toBe(true);
    expect(view.approvalOffered).toBe(true);
    expect(view.approvalDefaultSelected).toBe(true);
    expect(view.replyOffered).toBe(true);
    expect(view.hiddenWarning).toBe(false);
  });

  it("omits the redundant approval option for an already approved comment", () => {
    const view = linkApplicationCommentActionsView({
      originType: "COMMENT",
      applicationStatus: "PENDING",
      commentState: "ready",
      approved: true,
    });
    expect(view.approvalOffered).toBe(false);
    expect(view.approvalDefaultSelected).toBe(false);
    expect(view.replyOffered).toBe(true);
  });

  it("warns about hidden comments while keeping mutation controls available", () => {
    const view = linkApplicationCommentActionsView({
      originType: "COMMENT",
      applicationStatus: "PENDING",
      commentState: "ready",
      approved: false,
      hidden: true,
    });
    expect(view.hiddenWarning).toBe(true);
    expect(view.controlsEnabled).toBe(true);
  });

  it("disables controls when the source comment is deleted or failed to load", () => {
    for (const commentState of ["deleted", "error", "loading"] as const) {
      const view = linkApplicationCommentActionsView({
        originType: "COMMENT",
        applicationStatus: "PENDING",
        commentState,
      });
      expect(view.visible).toBe(true);
      expect(view.controlsEnabled).toBe(false);
      expect(view.approvalOffered).toBe(false);
      expect(view.replyOffered).toBe(false);
      expect(view.hiddenWarning).toBe(false);
    }
  });

  it("uses standalone mode for approved applications", () => {
    const view = linkApplicationCommentActionsView({
      originType: "COMMENT",
      applicationStatus: "APPROVED",
      commentState: "ready",
      approved: false,
    });
    expect(view.visible).toBe(true);
    expect(view.standalone).toBe(true);
  });

  it("keeps approving applications on the orchestrated approval path", () => {
    const view = linkApplicationCommentActionsView({
      originType: "COMMENT",
      applicationStatus: "APPROVING",
      commentState: "ready",
      approved: false,
    });
    expect(view.visible).toBe(true);
    expect(view.standalone).toBe(false);
  });
});

describe("normalizeOriginCommentActionIntent", () => {
  it("drops the approval selection when a reply is present", () => {
    expect(normalizeOriginCommentActionIntent({ approve: true, replyText: " 好的 " })).toEqual({
      approve: false,
      reply: "好的",
    });
  });

  it("keeps approval when the reply is blank", () => {
    expect(normalizeOriginCommentActionIntent({ approve: true, replyText: "  \n " })).toEqual({
      approve: true,
      reply: "",
    });
  });
});

describe("originCommentActionIntentEmpty", () => {
  it("treats an unselected approval and blank reply as empty", () => {
    expect(originCommentActionIntentEmpty({ approve: false, replyText: "   " })).toBe(true);
  });

  it("treats a selected approval or a non-blank reply as actionable", () => {
    expect(originCommentActionIntentEmpty({ approve: true, replyText: "" })).toBe(false);
    expect(originCommentActionIntentEmpty({ approve: false, replyText: "收到" })).toBe(false);
  });
});

describe("linkApplicationActionSummary", () => {
  it("summarizes link approval combined with comment approval", () => {
    expect(linkApplicationActionSummary({ includesLinkApproval: true, intent: { approve: true, replyText: "" } })).toBe(
      "通过申请，并通过来源评论",
    );
  });

  it("notes that a reply also approves the comment", () => {
    expect(
      linkApplicationActionSummary({ includesLinkApproval: true, intent: { approve: true, replyText: "欢迎" } }),
    ).toBe("通过申请，并回复来源评论（评论将随之通过）");
  });

  it("states plainly when no comment action is selected", () => {
    expect(
      linkApplicationActionSummary({ includesLinkApproval: true, intent: { approve: false, replyText: "" } }),
    ).toBe("仅通过申请，不处理来源评论");
  });

  it("summarizes standalone comment actions", () => {
    expect(
      linkApplicationActionSummary({ includesLinkApproval: false, intent: { approve: true, replyText: "" } }),
    ).toBe("将通过来源评论");
    expect(
      linkApplicationActionSummary({ includesLinkApproval: false, intent: { approve: false, replyText: "" } }),
    ).toBe("未选择评论操作");
  });
});

describe("isIndeterminateCommentRequestError", () => {
  it("treats axios errors without a response as indeterminate", () => {
    expect(isIndeterminateCommentRequestError({ isAxiosError: true, response: undefined })).toBe(true);
  });

  it("treats 5xx responses as indeterminate because the write may have been persisted", () => {
    expect(isIndeterminateCommentRequestError({ isAxiosError: true, response: { status: 500 } })).toBe(true);
    expect(isIndeterminateCommentRequestError({ isAxiosError: true, response: { status: 502 } })).toBe(true);
  });

  it("treats 4xx responses as determinate failures", () => {
    expect(isIndeterminateCommentRequestError({ isAxiosError: true, response: { status: 400 } })).toBe(false);
    expect(isIndeterminateCommentRequestError({ isAxiosError: true, response: { status: 403 } })).toBe(false);
    expect(isIndeterminateCommentRequestError({ isAxiosError: true, response: { status: 404 } })).toBe(false);
    expect(isIndeterminateCommentRequestError({ isAxiosError: true, response: { status: 409 } })).toBe(false);
  });

  it("treats non-axios errors as determinate", () => {
    expect(isIndeterminateCommentRequestError(new Error("boom"))).toBe(false);
  });
});

describe("originCommentOutcomeRequiresReplyConfirmation", () => {
  it("requires confirmation only after an indeterminate reply", () => {
    expect(originCommentOutcomeRequiresReplyConfirmation({ type: "indeterminate", action: "reply" })).toBe(true);
    expect(originCommentOutcomeRequiresReplyConfirmation({ type: "indeterminate", action: "approve" })).toBe(false);
    expect(
      originCommentOutcomeRequiresReplyConfirmation({
        type: "completed",
        approvedComment: false,
        replied: true,
      }),
    ).toBe(false);
  });
});

describe("originCommentActionSuccessMessage", () => {
  it("combines link and comment results", () => {
    expect(originCommentActionSuccessMessage({ type: "completed", approvedComment: true, replied: false }, true)).toBe(
      "已通过申请，并通过来源评论",
    );
    expect(originCommentActionSuccessMessage({ type: "completed", approvedComment: false, replied: true }, true)).toBe(
      "已通过申请，并回复来源评论",
    );
  });

  it("falls back to plain link approval when no comment action ran", () => {
    expect(originCommentActionSuccessMessage({ type: "completed", approvedComment: false, replied: false }, true)).toBe(
      "已通过申请",
    );
  });

  it("reports standalone comment results", () => {
    expect(originCommentActionSuccessMessage({ type: "completed", approvedComment: true, replied: false }, false)).toBe(
      "已通过来源评论",
    );
    expect(originCommentActionSuccessMessage({ type: "completed", approvedComment: false, replied: true }, false)).toBe(
      "已回复来源评论",
    );
  });
});

describe("originCommentActionFailureAlert", () => {
  it("states that the link succeeded while comment handling failed", () => {
    const alert = originCommentActionFailureAlert({
      outcome: { type: "failed", action: "approve" },
      includesLinkApproval: true,
    });
    expect(alert.title).toContain("链接已通过");
    expect(alert.title).toContain("通过来源评论失败");
    expect(alert.description).toContain("链接已通过且不受影响");
  });

  it("does not mention the link for standalone comment failures", () => {
    const alert = originCommentActionFailureAlert({
      outcome: { type: "failed", action: "reply" },
      includesLinkApproval: false,
    });
    expect(alert.title).not.toContain("链接已通过");
    expect(alert.title).toContain("回复来源评论失败");
  });

  it("reports an indeterminate reply without claiming failure", () => {
    const alert = originCommentActionFailureAlert({
      outcome: { type: "indeterminate", action: "reply", commentApproved: true, recentReplyCount: 3 },
      includesLinkApproval: true,
    });
    expect(alert.title).toContain("回复结果未知");
    expect(alert.description).not.toContain("回复失败");
    expect(alert.description).toContain("不会自动重试");
    expect(alert.description).toContain("来源评论当前已通过");
    expect(alert.description).toContain("最近共有 3 条回复");
    expect(alert.description).toContain("再决定是否重新提交");
  });

  it("notes the failed state refresh when an approval outcome is indeterminate", () => {
    const alert = originCommentActionFailureAlert({
      outcome: { type: "indeterminate", action: "approve" },
      includesLinkApproval: true,
    });
    expect(alert.title).toContain("评论审批结果未知");
    expect(alert.description).toContain("重新提交审批是安全的");
    expect(alert.description).toContain("当前状态刷新失败");
  });

  it("notes the failed state refresh when indeterminate details could not be loaded", () => {
    const alert = originCommentActionFailureAlert({
      outcome: { type: "indeterminate", action: "reply" },
      includesLinkApproval: false,
    });
    expect(alert.description).not.toContain("已刷新当前状态");
    expect(alert.description).toContain("当前状态刷新失败");
  });
});
