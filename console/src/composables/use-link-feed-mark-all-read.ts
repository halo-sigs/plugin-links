import { linksConsoleApiClient } from "@/api";
import { shallowRef } from "vue";

export interface LinkFeedMarkAllReadSummary {
  updatedCount: number;
}

export function useLinkFeedMarkAllRead() {
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
