import { linksConsoleApiClient } from "@/api";
import type { LinkFeedUnreadSummary } from "@/api/generated";
import { useQuery } from "@tanstack/vue-query";

export const QK_LINK_FEED_UNREAD_SUMMARY = "plugin:links:feed-unread-summary";

export interface LinkFeedUnreadSummaryQueryClient {
  invalidateQueries(options: { queryKey: unknown[] }): Promise<unknown> | unknown;
}

export function useLinkFeedUnreadSummary() {
  return useQuery<LinkFeedUnreadSummary>({
    queryKey: [QK_LINK_FEED_UNREAD_SUMMARY],
    queryFn: async () => {
      const { data } = await linksConsoleApiClient.feed.getLinkFeedUnreadSummary();
      return data;
    },
  });
}

export function linkFeedUnreadCount(summary: LinkFeedUnreadSummary | undefined, linkName: string | undefined) {
  if (!linkName) {
    return 0;
  }
  return summary?.unreadCountsByLinkName?.[linkName] || 0;
}

export function invalidateLinkFeedUnreadSummary(queryClient: LinkFeedUnreadSummaryQueryClient) {
  return queryClient.invalidateQueries({ queryKey: [QK_LINK_FEED_UNREAD_SUMMARY] });
}
