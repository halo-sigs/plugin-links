import { linksConsoleApiClient } from "@/api";
import type { LinkVerificationRequest, LinkVerificationTriggerResult } from "@/api/generated";
import { Toast } from "@halo-dev/components";
import { QK_GROUPS_WITH_LINKS } from "./use-link-fetch";

export interface LinkVerificationApi {
  verifyLinks(request: {
    linkVerificationRequest?: LinkVerificationRequest;
  }): Promise<{ data: LinkVerificationTriggerResult }>;
}

export interface LinkVerificationQueryClient {
  invalidateQueries(options: { queryKey: unknown[] }): Promise<unknown> | unknown;
}

export interface LinkVerificationToast {
  info(message: string): void;
  success(message: string): void;
}

export interface LinkVerificationOptions {
  linkApi?: LinkVerificationApi;
  queryClient: LinkVerificationQueryClient;
  request?: LinkVerificationRequest;
  showSuccess?: boolean;
  toast?: LinkVerificationToast;
}

export function startLinkVerification(options: LinkVerificationOptions) {
  void runLinkVerification(options);
}

export async function runLinkVerification({
  linkApi = linksConsoleApiClient.link,
  queryClient,
  request,
  showSuccess = false,
  toast = Toast,
}: LinkVerificationOptions) {
  try {
    const { data } = await linkApi.verifyLinks({
      linkVerificationRequest: normalizeLinkVerificationRequest(request),
    });

    if (showSuccess) {
      const message = linkVerificationResultMessage(data);
      if (hasAcceptedLinks(data)) {
        toast.success(message);
      } else {
        toast.info(message);
      }
    }

    return data;
  } catch {
    // Halo's API interceptor shows request failure toasts.
  } finally {
    await queryClient.invalidateQueries({ queryKey: [QK_GROUPS_WITH_LINKS] });
  }
}

export function normalizeLinkVerificationRequest(request: LinkVerificationRequest = {}): LinkVerificationRequest {
  const names = request.names?.map((name) => name.trim()).filter(Boolean);
  if (names?.length) {
    return { names };
  }

  const groupName = request.groupName?.trim();
  if (groupName) {
    return { groupName };
  }

  return {};
}

export function linkVerificationResultMessage(result: LinkVerificationTriggerResult) {
  const acceptedCount = result.acceptedCount ?? result.acceptedNames?.length ?? 0;
  if (acceptedCount) {
    return `已开始检测 ${acceptedCount} 个链接`;
  }

  const alreadyRunningCount = result.alreadyRunningCount ?? result.alreadyRunningNames?.length ?? 0;
  if (alreadyRunningCount) {
    return `${alreadyRunningCount} 个链接正在检测中`;
  }

  const skippedCount = result.skippedCount ?? result.skippedNames?.length ?? 0;
  if (skippedCount) {
    return "没有可检测的链接";
  }

  return "没有可检测的链接";
}

function hasAcceptedLinks(result: LinkVerificationTriggerResult) {
  return Boolean(result.acceptedCount || result.acceptedNames?.length);
}
