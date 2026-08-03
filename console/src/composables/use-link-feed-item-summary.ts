import { linksConsoleApiClient } from "@/api";
import type { LinkFeedItemSummary } from "@/api/generated";
import { useQuery } from "@tanstack/vue-query";
import { toValue, type MaybeRefOrGetter } from "vue";

export const QK_LINK_FEED_ITEM_SUMMARY = "plugin:links:feed-item-summary";

export interface LinkFeedItemSummaryQueryClient {
  invalidateQueries(options: { queryKey: unknown[] }): Promise<unknown> | unknown;
}

export function useLinkFeedItemSummary(enabled?: MaybeRefOrGetter<boolean>) {
  return useQuery<LinkFeedItemSummary>({
    queryKey: [QK_LINK_FEED_ITEM_SUMMARY],
    enabled: enabled === undefined ? true : () => Boolean(toValue(enabled)),
    queryFn: async () => {
      const { data } = await linksConsoleApiClient.feed.getLinkFeedItemSummary();
      return data;
    },
  });
}

export function invalidateLinkFeedItemSummary(queryClient: LinkFeedItemSummaryQueryClient) {
  return queryClient.invalidateQueries({ queryKey: [QK_LINK_FEED_ITEM_SUMMARY] });
}
