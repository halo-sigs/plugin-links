import { linksConsoleApiClient } from "@/api";
import type { LinkFeedItem } from "@/api/generated";
import { Toast } from "@halo-dev/components";
import { computed, ref, shallowRef, watch } from "vue";

export const QK_LINK_FEED_ITEMS = "plugin:links:feed-items";

export type LinkFeedReadStatus = "" | "unread" | "read";

export function useLinkFeedItems() {
  const items = ref<LinkFeedItem[]>([]);
  const selectedLinkName = shallowRef("");
  const selectedGroupName = shallowRef("");
  const selectedReadStatus = shallowRef<LinkFeedReadStatus>("");
  const nextBeforePublishedAt = shallowRef<string | undefined>();
  const nextBeforeId = shallowRef<string | undefined>();
  const hasNext = shallowRef(false);
  const isLoading = shallowRef(false);
  const isLoadingMore = shallowRef(false);
  const markingReadItemId = shallowRef("");

  const activeFilter = computed(() => ({
    linkName: selectedLinkName.value || undefined,
    groupName: selectedGroupName.value || undefined,
    read:
      selectedReadStatus.value === "read"
        ? true
        : selectedReadStatus.value === "unread"
          ? false
          : undefined,
  }));

  async function load({ append = false }: { append?: boolean } = {}) {
    if (append) {
      isLoadingMore.value = true;
    } else {
      isLoading.value = true;
      nextBeforePublishedAt.value = undefined;
      nextBeforeId.value = undefined;
    }
    try {
      const { data } = await linksConsoleApiClient.feed.listLinkFeedItems({
        ...activeFilter.value,
        beforePublishedAt: append ? nextBeforePublishedAt.value : undefined,
        beforeId: append ? nextBeforeId.value : undefined,
        limit: 30,
      });
      const pageItems = data.items || [];
      items.value = append ? [...items.value, ...pageItems] : pageItems;
      nextBeforePublishedAt.value = data.nextBeforePublishedAt;
      nextBeforeId.value = data.nextBeforeId;
      hasNext.value = data.hasNext ?? false;
    } finally {
      isLoading.value = false;
      isLoadingMore.value = false;
    }
  }

  function selectLink(name: string) {
    selectedLinkName.value = name;
    if (name) {
      selectedGroupName.value = "";
    }
  }

  function selectGroup(name: string) {
    selectedGroupName.value = name;
    if (name) {
      selectedLinkName.value = "";
    }
  }

  function selectReadStatus(status: LinkFeedReadStatus) {
    selectedReadStatus.value = status;
  }

  async function markItemRead(item: LinkFeedItem, read: boolean) {
    if (!item.id || markingReadItemId.value) {
      return;
    }
    markingReadItemId.value = item.id;
    try {
      await linksConsoleApiClient.feed.markLinkFeedItemRead({
        id: item.id,
        read,
      });
      item.read = read;
      if (activeFilter.value.read !== undefined && activeFilter.value.read !== read) {
        items.value = items.value.filter((current) => current.id !== item.id);
      }
    } catch {
      Toast.error("更新阅读状态失败");
    } finally {
      markingReadItemId.value = "";
    }
  }

  watch([selectedLinkName, selectedGroupName, selectedReadStatus], () => load(), {
    immediate: true,
  });

  return {
    items,
    selectedLinkName,
    selectedGroupName,
    selectedReadStatus,
    hasNext,
    isLoading,
    isLoadingMore,
    markingReadItemId,
    load,
    selectLink,
    selectGroup,
    selectReadStatus,
    markItemRead,
  };
}
