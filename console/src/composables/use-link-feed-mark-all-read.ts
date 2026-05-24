import { linksConsoleApiClient } from "@/api";
import type { LinkFeedItem } from "@/api/generated";
import { chunk } from "es-toolkit";
import { computed, shallowRef, toValue, type MaybeRefOrGetter } from "vue";

const MARK_READ_BATCH_SIZE = 5;

export interface LinkFeedMarkAllReadSummary {
  requestedCount: number;
  successCount: number;
  failureCount: number;
}

export function useLinkFeedMarkAllRead(items: MaybeRefOrGetter<LinkFeedItem[]>) {
  const isMarkingAllRead = shallowRef(false);

  const loadedUnreadItems = computed(() => {
    return toValue(items).filter((item) => item.id && !item.read);
  });

  const loadedUnreadItemIds = computed(() => {
    return loadedUnreadItems.value.map((item) => item.id).filter((id): id is string => !!id);
  });

  const loadedUnreadCount = computed(() => loadedUnreadItemIds.value.length);

  const hasLoadedUnreadItems = computed(() => loadedUnreadCount.value > 0);

  async function markAllRead(): Promise<LinkFeedMarkAllReadSummary | undefined> {
    if (isMarkingAllRead.value || !loadedUnreadItemIds.value.length) {
      return undefined;
    }

    const targetIds = [...loadedUnreadItemIds.value];
    const summary: LinkFeedMarkAllReadSummary = {
      requestedCount: targetIds.length,
      successCount: 0,
      failureCount: 0,
    };

    isMarkingAllRead.value = true;
    try {
      for (const idChunk of chunk(targetIds, MARK_READ_BATCH_SIZE)) {
        const results = await Promise.allSettled(
          idChunk.map((id) =>
            linksConsoleApiClient.feed.markLinkFeedItemRead({
              id,
              read: true,
            }),
          ),
        );
        summary.successCount += results.filter((result) => result.status === "fulfilled").length;
        summary.failureCount += results.filter((result) => result.status === "rejected").length;
      }
      return summary;
    } finally {
      isMarkingAllRead.value = false;
    }
  }

  return {
    loadedUnreadItems,
    loadedUnreadItemIds,
    loadedUnreadCount,
    hasLoadedUnreadItems,
    isMarkingAllRead,
    markAllRead,
  };
}
