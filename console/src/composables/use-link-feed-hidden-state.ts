import { linksConsoleApiClient } from "@/api";
import type { LinkFeedHiddenCount } from "@/api/generated";
import { QK_LINK_FEED_HIDDEN_ITEMS, QK_LINK_FEED_ITEMS } from "@/composables/use-link-feed";
import { invalidateLinkFeedUnreadSummary } from "@/composables/use-link-feed-unread-summary";
import { useQuery, useQueryClient, type QueryClient } from "@tanstack/vue-query";
import { shallowRef, toValue, type MaybeRefOrGetter } from "vue";

export const QK_LINK_FEED_HIDDEN_COUNT = "plugin:links:feed-hidden-count";

export interface LinkFeedHiddenStateSummary {
  requestedCount: number;
  updatedCount: number;
}

export function useLinkFeedHiddenCount(enabled?: MaybeRefOrGetter<boolean>) {
  return useQuery<LinkFeedHiddenCount>({
    queryKey: [QK_LINK_FEED_HIDDEN_COUNT],
    enabled: enabled === undefined ? true : () => Boolean(toValue(enabled)),
    queryFn: async () => {
      const { data } = await linksConsoleApiClient.feed.getLinkFeedHiddenCount();
      return data;
    },
  });
}

export function useLinkFeedHiddenState() {
  const queryClient = useQueryClient();
  const isUpdating = shallowRef(false);

  async function setHiddenState(ids: string[], hidden: boolean): Promise<LinkFeedHiddenStateSummary | undefined> {
    const distinctIds = [...new Set(ids)];
    if (!distinctIds.length || isUpdating.value) {
      return undefined;
    }

    isUpdating.value = true;
    try {
      const { data } = await linksConsoleApiClient.feed.updateLinkFeedItemsHiddenState({
        linkFeedHiddenStateRequest: {
          ids: distinctIds,
          hidden,
        },
      });
      await invalidateLinkFeedHiddenStateQueries(queryClient);
      return {
        requestedCount: data.requestedCount || 0,
        updatedCount: data.updatedCount || 0,
      };
    } catch {
      return undefined;
    } finally {
      isUpdating.value = false;
    }
  }

  return {
    isUpdating,
    setHiddenState,
  };
}

export function invalidateLinkFeedHiddenStateQueries(queryClient: QueryClient) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: [QK_LINK_FEED_ITEMS] }),
    queryClient.invalidateQueries({ queryKey: [QK_LINK_FEED_HIDDEN_ITEMS] }),
    queryClient.invalidateQueries({ queryKey: [QK_LINK_FEED_HIDDEN_COUNT] }),
    invalidateLinkFeedUnreadSummary(queryClient),
  ]);
}
