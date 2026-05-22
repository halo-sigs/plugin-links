import { linksConsoleApiClient } from "@/api";
import type { LinkFeedItem } from "@/api/generated";
import { Toast } from "@halo-dev/components";
import { computed, ref, shallowRef, watch } from "vue";

export const QK_LINK_FEED_ITEMS = "plugin:links:feed-items";

export function useLinkFeedItems() {
  const items = ref<LinkFeedItem[]>([]);
  const selectedLinkName = shallowRef("");
  const selectedGroupName = shallowRef("");
  const nextBeforePublishedAt = shallowRef<string | undefined>();
  const nextBeforeId = shallowRef<string | undefined>();
  const hasNext = shallowRef(false);
  const isLoading = shallowRef(false);
  const isLoadingMore = shallowRef(false);
  const refreshingLinkName = shallowRef("");

  const activeFilter = computed(() => ({
    linkName: selectedLinkName.value || undefined,
    groupName: selectedGroupName.value || undefined,
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

  async function refreshLink(name: string) {
    if (!name || refreshingLinkName.value) {
      return;
    }
    refreshingLinkName.value = name;
    try {
      await linksConsoleApiClient.feed.refreshLinkFeed({ name });
      Toast.success("刷新 RSS 成功");
      await load();
    } catch {
      Toast.error("刷新 RSS 失败");
    } finally {
      refreshingLinkName.value = "";
    }
  }

  watch([selectedLinkName, selectedGroupName], () => load(), {
    immediate: true,
  });

  return {
    items,
    selectedLinkName,
    selectedGroupName,
    hasNext,
    isLoading,
    isLoadingMore,
    refreshingLinkName,
    load,
    selectLink,
    selectGroup,
    refreshLink,
  };
}
