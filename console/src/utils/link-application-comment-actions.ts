import axios from "axios";

export type LinkApplicationOriginCommentState = "loading" | "ready" | "deleted" | "error";

export interface LinkApplicationCommentActionsViewInput {
  originType: string;
  /** Use the effective status; pass APPROVED when link approval just completed locally. */
  applicationStatus?: string;
  commentState: LinkApplicationOriginCommentState;
  approved?: boolean;
  hidden?: boolean;
}

export interface LinkApplicationCommentActionsView {
  /** Whether the source-Comment handling section is shown at all. */
  visible: boolean;
  /** Whether Comment handling runs as a separate action instead of following link approval. */
  standalone: boolean;
  /** Whether the source Comment currently exists and can be mutated. */
  controlsEnabled: boolean;
  /** Whether an approval option is offered (source exists and is not approved yet). */
  approvalOffered: boolean;
  approvalDefaultSelected: boolean;
  replyOffered: boolean;
  hiddenWarning: boolean;
}

const hiddenCommentActionsView: LinkApplicationCommentActionsView = {
  visible: false,
  standalone: false,
  controlsEnabled: false,
  approvalOffered: false,
  approvalDefaultSelected: false,
  replyOffered: false,
  hiddenWarning: false,
};

export function linkApplicationCommentActionsView(
  input: LinkApplicationCommentActionsViewInput,
): LinkApplicationCommentActionsView {
  if (input.originType !== "COMMENT" || input.applicationStatus === "REJECTED") {
    return hiddenCommentActionsView;
  }
  const ready = input.commentState === "ready";
  return {
    visible: true,
    standalone: input.applicationStatus === "APPROVED",
    controlsEnabled: ready,
    approvalOffered: ready && !input.approved,
    approvalDefaultSelected: ready && !input.approved,
    replyOffered: ready,
    hiddenWarning: ready && !!input.hidden,
  };
}

export interface OriginCommentActionIntent {
  approve: boolean;
  replyText: string;
}

export interface NormalizedOriginCommentActionIntent {
  approve: boolean;
  reply: string;
}

/**
 * Trims the reply and drops the approval selection when a reply is present, because Halo's
 * reply flow already approves the parent Comment.
 */
export function normalizeOriginCommentActionIntent(
  intent: OriginCommentActionIntent,
): NormalizedOriginCommentActionIntent {
  const reply = intent.replyText.trim();
  return { approve: intent.approve && !reply, reply };
}

export function originCommentActionIntentEmpty(intent: OriginCommentActionIntent): boolean {
  const normalized = normalizeOriginCommentActionIntent(intent);
  return !normalized.approve && !normalized.reply;
}

export function linkApplicationActionSummary(options: {
  includesLinkApproval: boolean;
  intent: OriginCommentActionIntent;
}): string {
  const { approve, reply } = normalizeOriginCommentActionIntent(options.intent);
  const commentPart = reply ? "回复来源评论（评论将随之通过）" : approve ? "通过来源评论" : "";
  if (options.includesLinkApproval) {
    return commentPart ? `通过申请，并${commentPart}` : "仅通过申请，不处理来源评论";
  }
  return commentPart ? `将${commentPart}` : "未选择评论操作";
}

export type OriginCommentActionOutcome =
  | { type: "completed"; approvedComment: boolean; replied: boolean }
  | { type: "failed"; action: "approve" | "reply"; status?: number }
  | { type: "indeterminate"; action: "approve" | "reply"; commentApproved?: boolean; recentReplyCount?: number };

export function originCommentOutcomeRequiresReplyConfirmation(outcome: OriginCommentActionOutcome): boolean {
  return outcome.type === "indeterminate" && outcome.action === "reply";
}

/**
 * A Comment request is indeterminate when the response is lost (timeout or network
 * interruption) or the server fails with 5xx after the write may have been persisted.
 * Only client-facing 4xx responses (validation, authorization, conflict, not found) are
 * determinate failures.
 */
export function isIndeterminateCommentRequestError(error: unknown): boolean {
  if (!axios.isAxiosError(error)) {
    return false;
  }
  const status = error.response?.status;
  return status === undefined || status >= 500;
}

export function originCommentActionSuccessMessage(
  outcome: Extract<OriginCommentActionOutcome, { type: "completed" }>,
  includesLinkApproval: boolean,
): string {
  const commentPart = outcome.replied ? "回复来源评论" : outcome.approvedComment ? "通过来源评论" : "";
  if (includesLinkApproval) {
    return commentPart ? `已通过申请，并${commentPart}` : "已通过申请";
  }
  return commentPart ? `已${commentPart}` : "已完成";
}

export interface OriginCommentActionFailureAlert {
  title: string;
  description: string;
}

export function originCommentActionFailureAlert(options: {
  outcome: Exclude<OriginCommentActionOutcome, { type: "completed" }>;
  includesLinkApproval: boolean;
}): OriginCommentActionFailureAlert {
  const linkPart = options.includesLinkApproval ? "链接已通过，但" : "";
  if (options.outcome.type === "failed") {
    const actionLabel = options.outcome.action === "approve" ? "通过来源评论" : "回复来源评论";
    return {
      title: `${linkPart}${actionLabel}失败`,
      description: options.includesLinkApproval
        ? "链接已通过且不受影响。可在下方重试仍然适用的评论操作。"
        : "可重试仍然适用的评论操作。",
    };
  }
  const stateParts: string[] = [];
  if (options.outcome.commentApproved !== undefined) {
    stateParts.push(`来源评论当前${options.outcome.commentApproved ? "已通过" : "未通过"}`);
  }
  if (options.outcome.recentReplyCount !== undefined) {
    stateParts.push(`最近共有 ${options.outcome.recentReplyCount} 条回复`);
  }
  const stateText = stateParts.length ? `已刷新当前状态：${stateParts.join("，")}。` : "当前状态刷新失败。";
  if (options.outcome.action === "approve") {
    return {
      title: `${linkPart}评论审批结果未知`,
      description: `网络或服务异常导致无法确认评论审批是否生效，系统不会自动重试；重新提交审批是安全的，已生效时会自动跳过。${stateText}请核对评论状态后，再决定是否重新提交。`,
    };
  }
  return {
    title: `${linkPart}回复结果未知`,
    description: `网络异常导致无法确认回复是否提交成功，系统不会自动重试。${stateText}请核对评论与回复状态后，再决定是否重新提交。`,
  };
}
