import type { ApprovalRequest, ApproveRequest, LinkApplication, LinkApplicationCleanupResult } from "@/api/generated";
import axios from "axios";

export interface LinkApplicationHistoryFilterInput {
  /** Empty string means no status filter. */
  status?: string;
  /** Empty string means no origin filter. */
  originType?: string;
}

export interface LinkApplicationQuery {
  page: number;
  size: number;
  status?: string;
  originType?: string;
}

function normalizeFilterValue(value: string | undefined): string | undefined {
  return value ? value : undefined;
}

export function buildLinkApplicationQuery(
  filter: LinkApplicationHistoryFilterInput,
  pagination: { page: number; size: number },
): LinkApplicationQuery {
  return {
    page: pagination.page,
    size: pagination.size,
    status: normalizeFilterValue(filter.status),
    originType: normalizeFilterValue(filter.originType),
  };
}

export function buildLinkApplicationCleanupParams(filter: LinkApplicationHistoryFilterInput) {
  return {
    status: normalizeFilterValue(filter.status),
    originType: normalizeFilterValue(filter.originType),
  };
}

export interface LinkApplicationStatusMeta {
  label: string;
  tagType: "default" | "primary" | "success" | "warning" | "danger";
}

export function linkApplicationStatusMeta(status: string | undefined): LinkApplicationStatusMeta {
  switch (status) {
    case "PENDING":
      return { label: "待审核", tagType: "warning" };
    case "APPROVING":
      return { label: "审批中", tagType: "primary" };
    case "APPROVED":
      return { label: "已通过", tagType: "success" };
    case "REJECTED":
      return { label: "已拒绝", tagType: "danger" };
    default:
      return { label: status || "未知", tagType: "default" };
  }
}

export type LinkApplicationReviewMode = "editable" | "resume" | "readonly";

export interface LinkApplicationApprovalFormData extends Omit<ApproveRequest, "feedUrls"> {
  feedUrlsText?: string;
}

export function linkApplicationReviewMode(application: LinkApplication): LinkApplicationReviewMode {
  switch (application.spec.status) {
    case "PENDING":
      return "editable";
    case "APPROVING":
      return "resume";
    default:
      return "readonly";
  }
}

export function buildLinkApplicationApprovalRequest(data: LinkApplicationApprovalFormData): ApproveRequest {
  return {
    url: data.url?.trim(),
    displayName: data.displayName?.trim(),
    logo: data.logo ?? "",
    description: data.description ?? "",
    groupName: data.groupName || undefined,
    backlink: data.backlink?.trim() || "",
    feedUrls: [
      ...new Set(
        (data.feedUrlsText || "")
          .split(/\r?\n/)
          .map((feedUrl) => feedUrl.trim())
          .filter(Boolean),
      ),
    ],
  };
}

export function linkApplicationEffectiveFields(application: LinkApplication): ApprovalRequest {
  const frozenRequest = application.spec.approval?.request;
  if (frozenRequest) {
    return frozenRequest;
  }
  return {
    url: application.spec.url,
    displayName: application.spec.displayName,
    logo: application.spec.logo,
    description: application.spec.description,
    backlink: application.spec.backlink,
    feedUrls: application.spec.feedUrls,
  };
}

export function linkApplicationRejectDescription(application: LinkApplication): string {
  const displayName = application.spec.displayName;
  if (application.spec.origin.type === "COMMENT") {
    return `拒绝 "${displayName}" 的申请后，该链接不会再被评论识别创建申请，但访客仍可通过表单再次提交。`;
  }
  return `拒绝 "${displayName}" 的申请后，在拒绝记录存在期间，该链接无法再次提交申请，也不会再被评论识别创建。删除拒绝记录可解除限制。`;
}

export function linkApplicationCleanupDescription(options: {
  total: number;
  status?: string;
  originType?: string;
}): string {
  const parts = [`将删除当前筛选条件下的全部 ${options.total} 条申请记录（审批中的申请会保留），此操作不可恢复。`];
  const mayIncludeResubmissionBlocking =
    (!options.status || options.status === "PENDING" || options.status === "REJECTED") &&
    (!options.originType || options.originType === "FORM");
  if (mayIncludeResubmissionBlocking) {
    parts.push("其中待审核或已拒绝的表单申请被删除后，对应链接可能可以再次提交申请。");
  }
  const mayIncludeApproved = !options.status || options.status === "APPROVED";
  if (mayIncludeApproved) {
    parts.push("删除已通过的申请不会删除已创建的链接。");
  }
  return parts.join("");
}

export function linkApplicationCleanupSummary(result: LinkApplicationCleanupResult): string {
  const segments = [`共匹配 ${result.matched ?? 0} 条申请`, `已删除 ${result.deleted ?? 0} 条`];
  if ((result.skipped ?? 0) > 0) {
    segments.push(`跳过 ${result.skipped} 条审批中的申请`);
  }
  if ((result.failed ?? 0) > 0) {
    segments.push(`${result.failed} 条删除失败`);
  }
  return `${segments.join("，")}。`;
}

export type LinkApplicationOriginCommentErrorState = "unavailable" | "forbidden" | "error";

export function linkApplicationOriginCommentErrorState(error: unknown): LinkApplicationOriginCommentErrorState {
  const status = axios.isAxiosError(error) ? error.response?.status : undefined;
  if (status === 404) {
    return "unavailable";
  }
  if (status === 401 || status === 403) {
    return "forbidden";
  }
  return "error";
}
