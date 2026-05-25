import { linksConsoleApiClient } from "@/api";
import type { Link, LinkFeedRefreshResult } from "@/api/generated";
import { useQueryClient } from "@tanstack/vue-query";
import { shallowRef } from "vue";
import { classifyLinkFeedRefreshResult } from "./link-feed-refresh-summary";
import { invalidateLinkFeedUnreadSummary } from "./use-link-feed-unread-summary";

export interface LinkFeedRefreshFailure {
  link: Link;
  error: unknown;
}

export interface LinkFeedRefreshSuccess {
  link: Link;
  result: LinkFeedRefreshResult;
}

export interface LinkFeedRefreshSummary {
  totalCount: number;
  successCount: number;
  failureCount: number;
  partialFailureCount: number;
  successes: LinkFeedRefreshSuccess[];
  failures: LinkFeedRefreshFailure[];
}

export function useLinkFeedRefresh() {
  const queryClient = useQueryClient();
  const isRefreshing = shallowRef(false);
  const totalCount = shallowRef(0);
  const completedCount = shallowRef(0);

  async function refreshLinks(targets: Link | Link[]) {
    if (isRefreshing.value) {
      return undefined;
    }

    const links = Array.isArray(targets) ? targets : [targets];
    const summary: LinkFeedRefreshSummary = {
      totalCount: links.length,
      successCount: 0,
      failureCount: 0,
      partialFailureCount: 0,
      successes: [],
      failures: [],
    };

    isRefreshing.value = true;
    totalCount.value = links.length;
    completedCount.value = 0;

    try {
      for (const link of links) {
        try {
          const { data } = await linksConsoleApiClient.feed.refreshLinkFeed({
            name: link.metadata.name,
          });
          const refreshState = classifyLinkFeedRefreshResult(data);
          if (refreshState === "failed") {
            summary.failureCount += 1;
            summary.failures.push({ link, error: data });
          } else if (refreshState === "partial") {
            summary.partialFailureCount += 1;
            summary.successes.push({ link, result: data });
          } else {
            summary.successCount += 1;
            summary.successes.push({ link, result: data });
          }
        } catch (error) {
          summary.failureCount += 1;
          summary.failures.push({ link, error });
        } finally {
          completedCount.value += 1;
        }
      }

      await invalidateLinkFeedUnreadSummary(queryClient);
      return summary;
    } finally {
      isRefreshing.value = false;
    }
  }

  return {
    isRefreshing,
    totalCount,
    completedCount,
    refreshLinks,
  };
}
