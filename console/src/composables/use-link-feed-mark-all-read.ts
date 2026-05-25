import { linksConsoleApiClient } from "@/api";
import { useQueryClient } from "@tanstack/vue-query";
import { shallowRef } from "vue";
import { invalidateLinkFeedUnreadSummary } from "./use-link-feed-unread-summary";

export interface LinkFeedMarkAllReadSummary {
  updatedCount: number;
}

export function useLinkFeedMarkAllRead() {
  const queryClient = useQueryClient();
  const isMarkingAllRead = shallowRef(false);

  async function markAllRead(linkName?: string): Promise<LinkFeedMarkAllReadSummary | undefined> {
    if (isMarkingAllRead.value) {
      return undefined;
    }

    isMarkingAllRead.value = true;
    try {
      const { data } = await linksConsoleApiClient.feed.markLinkFeedItemsRead({
        linkName: linkName || undefined,
      });
      await invalidateLinkFeedUnreadSummary(queryClient);
      return {
        updatedCount: data.updatedCount || 0,
      };
    } finally {
      isMarkingAllRead.value = false;
    }
  }

  return {
    isMarkingAllRead,
    markAllRead,
  };
}
