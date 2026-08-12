import type { LinkApplication, LinkApplicationOriginComment } from "@/api/generated";
import { QK_LINK_APPLICATION_ORIGIN_COMMENT } from "@/composables/use-link-application";
import { plainTextToSafeHtml } from "@/utils/comment-content";
import {
  isIndeterminateCommentRequestError,
  normalizeOriginCommentActionIntent,
  originCommentActionIntentEmpty,
  type OriginCommentActionIntent,
  type OriginCommentActionOutcome,
} from "@/utils/link-application-comment-actions";
import { consoleApiClient, coreApiClient } from "@halo-dev/api-client";
import { useQueryClient } from "@tanstack/vue-query";
import axios from "axios";
import { shallowRef, unref, type MaybeRef } from "vue";

function axiosErrorStatus(error: unknown): number | undefined {
  return axios.isAxiosError(error) ? error.response?.status : undefined;
}

export async function approveOriginComment(name: string) {
  await coreApiClient.content.comment.patchComment({
    name,
    jsonPatchInner: [
      { op: "add", path: "/spec/approved", value: true },
      { op: "add", path: "/spec/approvedTime", value: new Date().toISOString() },
    ],
  });
}

export async function replyToOriginComment(name: string, raw: string) {
  const { data } = await consoleApiClient.content.comment.createReply({
    name,
    replyRequest: {
      raw,
      content: plainTextToSafeHtml(raw),
      allowNotification: true,
      quoteReply: undefined,
    },
  });
  return data;
}

export async function listLatestOriginCommentReplies(commentName: string) {
  const { data } = await consoleApiClient.content.reply.listReplies({
    commentName,
    page: 1,
    size: 20,
    sort: ["metadata.creationTimestamp,desc"],
  });
  return data;
}

export interface RunOriginCommentActionsOptions {
  commentName: string;
  intent: OriginCommentActionIntent;
  /** Returns the current source state, or undefined when it cannot be loaded. */
  refetchOriginComment: () => Promise<LinkApplicationOriginComment | undefined>;
}

/**
 * Runs the selected Comment operations against Halo's existing APIs. A reply never follows an
 * approval patch, because Halo's reply flow already approves the parent Comment. An
 * indeterminate reply is never resubmitted automatically; current Comment and reply state is
 * refreshed so the reviewer can confirm the outcome before any manual resubmission.
 */
export async function runOriginCommentActions(
  options: RunOriginCommentActionsOptions,
): Promise<OriginCommentActionOutcome> {
  const { approve, reply } = normalizeOriginCommentActionIntent(options.intent);

  if (reply) {
    try {
      await replyToOriginComment(options.commentName, reply);
      return { type: "completed", approvedComment: false, replied: true };
    } catch (error) {
      if (!isIndeterminateCommentRequestError(error)) {
        return { type: "failed", action: "reply", status: axiosErrorStatus(error) };
      }
      const [commentResult, repliesResult] = await Promise.allSettled([
        options.refetchOriginComment(),
        listLatestOriginCommentReplies(options.commentName),
      ]);
      return {
        type: "indeterminate",
        action: "reply",
        commentApproved: commentResult.status === "fulfilled" ? commentResult.value?.approved : undefined,
        recentReplyCount: repliesResult.status === "fulfilled" ? repliesResult.value?.items?.length : undefined,
      };
    }
  }

  if (approve) {
    const latest = await options.refetchOriginComment().catch(() => undefined);
    if (latest?.approved) {
      return { type: "completed", approvedComment: true, replied: false };
    }
    try {
      await approveOriginComment(options.commentName);
      return { type: "completed", approvedComment: true, replied: false };
    } catch (error) {
      if (isIndeterminateCommentRequestError(error)) {
        // The patch may have been applied; confirm the actual state instead of claiming failure.
        const refreshed = await options.refetchOriginComment().catch(() => undefined);
        if (refreshed?.approved) {
          return { type: "completed", approvedComment: true, replied: false };
        }
        if (!refreshed) {
          // The state refresh also failed, so the outcome is unknown; approval stays safe to
          // resubmit because an already-approved source is skipped above.
          return { type: "indeterminate", action: "approve" };
        }
      }
      return { type: "failed", action: "approve", status: axiosErrorStatus(error) };
    }
  }

  return { type: "completed", approvedComment: false, replied: false };
}

export interface ApproveLinkThenHandleOriginCommentResult {
  linkApproved: boolean;
  commentOutcome?: OriginCommentActionOutcome;
}

/**
 * Keeps link approval authoritative: Comment handling only runs after link approval completes,
 * and a failed link approval prevents every Comment request.
 */
export async function approveLinkThenHandleOriginComment(options: {
  approveLink: () => Promise<unknown>;
  handleComment: () => Promise<OriginCommentActionOutcome>;
}): Promise<ApproveLinkThenHandleOriginCommentResult> {
  try {
    await options.approveLink();
  } catch {
    return { linkApproved: false };
  }
  return { linkApproved: true, commentOutcome: await options.handleComment() };
}

export function useOriginCommentActions(options: {
  application: MaybeRef<LinkApplication>;
  refetchOriginComment: () => Promise<LinkApplicationOriginComment | undefined>;
}) {
  const queryClient = useQueryClient();
  const isProcessing = shallowRef(false);

  async function handleOriginComment(intent: OriginCommentActionIntent): Promise<OriginCommentActionOutcome> {
    // No selected action (including form-origin applications) is a definite success, never a failure.
    if (originCommentActionIntentEmpty(intent)) {
      return { type: "completed", approvedComment: false, replied: false };
    }
    const application = unref(options.application);
    const commentName = application.spec.origin.comment?.name;
    if (!commentName) {
      return { type: "failed", action: "approve" };
    }
    isProcessing.value = true;
    try {
      return await runOriginCommentActions({
        commentName,
        intent,
        refetchOriginComment: options.refetchOriginComment,
      });
    } finally {
      isProcessing.value = false;
      queryClient.invalidateQueries({
        queryKey: [QK_LINK_APPLICATION_ORIGIN_COMMENT, application.metadata.name],
      });
    }
  }

  return { isProcessing, handleOriginComment };
}
