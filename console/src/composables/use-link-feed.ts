import { linksConsoleApiClient } from "@/api";
import type { LinkFeedItem } from "@/api/generated";
import { Toast } from "@halo-dev/components";
import { computed, ref, shallowRef, watch } from "vue";

export const QK_LINK_FEED_ITEMS = "plugin:links:feed-items";

export type LinkFeedReadStatus = "" | "unread" | "read";
export type LinkFeedBooleanStatus = "" | "true" | "false";

export function useLinkFeedItems() {
  const items = ref<LinkFeedItem[]>([]);
  const selectedLinkName = shallowRef("");
  const selectedGroupName = shallowRef("");
  const selectedReadStatus = shallowRef<LinkFeedReadStatus>("");
  const selectedFavoriteStatus = shallowRef<LinkFeedBooleanStatus>("");
  const selectedReadLaterStatus = shallowRef<LinkFeedBooleanStatus>("");
  const nextBeforePublishedAt = shallowRef<string | undefined>();
  const nextBeforeId = shallowRef<string | undefined>();
  const hasNext = shallowRef(false);
  const isLoading = shallowRef(false);
  const isLoadingMore = shallowRef(false);
  const markingReadItemId = shallowRef("");
  const markingFavoriteItemId = shallowRef("");
  const markingReadLaterItemId = shallowRef("");

  const activeFilter = computed(() => ({
    linkName: selectedLinkName.value || undefined,
    groupName: selectedGroupName.value || undefined,
    read:
      selectedReadStatus.value === "read"
        ? true
        : selectedReadStatus.value === "unread"
          ? false
          : undefined,
    favorite: booleanFilterValue(selectedFavoriteStatus.value),
    readLater: booleanFilterValue(selectedReadLaterStatus.value),
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

  function selectFavoriteStatus(status: LinkFeedBooleanStatus) {
    selectedFavoriteStatus.value = status;
  }

  function selectReadLaterStatus(status: LinkFeedBooleanStatus) {
    selectedReadLaterStatus.value = status;
  }

  async function markItemRead(item: LinkFeedItem, read: boolean) {
    if (!item.id || markingReadItemId.value) {
      return false;
    }
    markingReadItemId.value = item.id;
    try {
      await linksConsoleApiClient.feed.markLinkFeedItemRead({
        id: item.id,
        read,
      });
      item.read = read;
      removeIfExcluded(item);
      return true;
    } catch {
      Toast.error("更新阅读状态失败");
      return false;
    } finally {
      markingReadItemId.value = "";
    }
  }

  async function markItemFavorite(item: LinkFeedItem, favorite: boolean) {
    if (!item.id || markingFavoriteItemId.value) {
      return false;
    }
    markingFavoriteItemId.value = item.id;
    try {
      await linksConsoleApiClient.feed.markLinkFeedItemFavorite({
        id: item.id,
        favorite,
      });
      item.favorite = favorite;
      removeIfExcluded(item);
      return true;
    } catch {
      Toast.error("更新收藏状态失败");
      return false;
    } finally {
      markingFavoriteItemId.value = "";
    }
  }

  async function markItemReadLater(item: LinkFeedItem, readLater: boolean) {
    if (!item.id || markingReadLaterItemId.value) {
      return false;
    }
    markingReadLaterItemId.value = item.id;
    try {
      await linksConsoleApiClient.feed.markLinkFeedItemReadLater({
        id: item.id,
        readLater,
      });
      item.readLater = readLater;
      removeIfExcluded(item);
      return true;
    } catch {
      Toast.error("更新稍后阅读状态失败");
      return false;
    } finally {
      markingReadLaterItemId.value = "";
    }
  }

  async function openItem(item: LinkFeedItem) {
    const markedRead = item.read || (await markItemRead(item, true));
    if (markedRead && item.readLater) {
      await markItemReadLater(item, false);
    }
  }

  function removeIfExcluded(item: LinkFeedItem) {
    if (!matchesActiveFilter(item)) {
      items.value = items.value.filter((current) => current.id !== item.id);
    }
  }

  function matchesActiveFilter(item: LinkFeedItem) {
    const filter = activeFilter.value;
    return (
      (filter.read === undefined || Boolean(item.read) === filter.read)
      && (filter.favorite === undefined || Boolean(item.favorite) === filter.favorite)
      && (filter.readLater === undefined || Boolean(item.readLater) === filter.readLater)
    );
  }

  function booleanFilterValue(status: LinkFeedBooleanStatus) {
    if (status === "true") {
      return true;
    }
    if (status === "false") {
      return false;
    }
    return undefined;
  }

  watch(
    [
      selectedLinkName,
      selectedGroupName,
      selectedReadStatus,
      selectedFavoriteStatus,
      selectedReadLaterStatus,
    ],
    () => load(),
    {
      immediate: true,
    },
  );

  return {
    items,
    selectedLinkName,
    selectedGroupName,
    selectedReadStatus,
    selectedFavoriteStatus,
    selectedReadLaterStatus,
    hasNext,
    isLoading,
    isLoadingMore,
    markingReadItemId,
    markingFavoriteItemId,
    markingReadLaterItemId,
    load,
    selectLink,
    selectGroup,
    selectReadStatus,
    selectFavoriteStatus,
    selectReadLaterStatus,
    markItemRead,
    markItemFavorite,
    markItemReadLater,
    openItem,
  };
}
