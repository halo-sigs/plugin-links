import { linksConsoleApiClient } from "@/api";
import type { LinkFeedItem, LinkFeedItemPage } from "@/api/generated";
import { useInfiniteQuery, useMutation, useQueryClient } from "@tanstack/vue-query";
import { computed, shallowRef, toValue, type MaybeRefOrGetter } from "vue";

export const QK_LINK_FEED_ITEMS = "plugin:links:feed-items";
const LINK_FEED_PAGE_SIZE = 30;

export type LinkFeedReadStatus = "" | "unread" | "read";

export interface LinkFeedItemsFilter {
  linkName?: string;
  groupName?: string;
  read?: boolean;
  favorite?: boolean;
  readLater?: boolean;
}

export interface UseLinkFeedItemsOptions {
  autoLoad?: boolean;
  enabled?: MaybeRefOrGetter<boolean>;
  fixedFilter?: MaybeRefOrGetter<LinkFeedItemsFilter | undefined>;
}

interface LinkFeedPageCursor {
  beforePublishedAt?: string;
  beforeId?: string;
}

interface MarkReadVariables {
  id: string;
  read: boolean;
}

interface MarkFavoriteVariables {
  id: string;
  favorite: boolean;
}

interface MarkReadLaterVariables {
  id: string;
  readLater: boolean;
}

export function useLinkFeedItems(options: UseLinkFeedItemsOptions = {}) {
  const queryClient = useQueryClient();
  const { autoLoad = true } = options;
  const selectedLinkName = shallowRef("");
  const selectedReadStatus = shallowRef<LinkFeedReadStatus>("");
  const queryEnabled = computed(() => {
    return options.enabled === undefined ? autoLoad : toValue(options.enabled);
  });

  const activeFilter = computed<LinkFeedItemsFilter>(() => ({
    linkName: selectedLinkName.value || undefined,
    read: selectedReadStatus.value === "read" ? true : selectedReadStatus.value === "unread" ? false : undefined,
    ...toValue(options.fixedFilter),
  }));

  const queryKey = computed(() => [QK_LINK_FEED_ITEMS, activeFilter.value] as const);

  const query = useInfiniteQuery<LinkFeedItemPage>({
    queryKey,
    enabled: queryEnabled,
    queryFn: async ({ pageParam }) => {
      const cursor = pageParam as LinkFeedPageCursor | undefined;
      const { data } = await linksConsoleApiClient.feed.listLinkFeedItems({
        ...activeFilter.value,
        beforePublishedAt: cursor?.beforePublishedAt,
        beforeId: cursor?.beforeId,
        limit: LINK_FEED_PAGE_SIZE,
      });
      return data;
    },
    getNextPageParam: (lastPage) => {
      if (!lastPage.hasNext) {
        return undefined;
      }
      return {
        beforePublishedAt: lastPage.nextBeforePublishedAt,
        beforeId: lastPage.nextBeforeId,
      } satisfies LinkFeedPageCursor;
    },
  });

  const items = computed(() => query.data.value?.pages.flatMap((page) => page.items || []) || []);
  const hasNext = computed(() => Boolean(query.hasNextPage?.value));
  const isLoading = computed(
    () => query.isLoading.value || (query.isFetching.value && !query.isFetchingNextPage.value),
  );
  const isLoadingMore = computed(() => query.isFetchingNextPage.value);

  const readMutation = useMutation<void, unknown, MarkReadVariables>({
    mutationFn: async ({ id, read }) => {
      await linksConsoleApiClient.feed.markLinkFeedItemRead({ id, read });
    },
    onSuccess: invalidateFeedItems,
  });

  const favoriteMutation = useMutation<void, unknown, MarkFavoriteVariables>({
    mutationFn: async ({ id, favorite }) => {
      await linksConsoleApiClient.feed.markLinkFeedItemFavorite({ id, favorite });
    },
    onSuccess: invalidateFeedItems,
  });

  const readLaterMutation = useMutation<void, unknown, MarkReadLaterVariables>({
    mutationFn: async ({ id, readLater }) => {
      await linksConsoleApiClient.feed.markLinkFeedItemReadLater({ id, readLater });
    },
    onSuccess: invalidateFeedItems,
  });

  const markingReadItemId = computed(() =>
    readMutation.isPending.value ? readMutation.variables.value?.id || "" : "",
  );
  const markingFavoriteItemId = computed(() =>
    favoriteMutation.isPending.value ? favoriteMutation.variables.value?.id || "" : "",
  );
  const markingReadLaterItemId = computed(() =>
    readLaterMutation.isPending.value ? readLaterMutation.variables.value?.id || "" : "",
  );

  async function invalidateFeedItems() {
    await queryClient.invalidateQueries({ queryKey: [QK_LINK_FEED_ITEMS] });
  }

  async function reload() {
    await queryClient.resetQueries({ queryKey: queryKey.value, exact: true });
  }

  async function fetchNextPage() {
    if (!query.hasNextPage?.value || query.isFetchingNextPage.value) {
      return;
    }
    await query.fetchNextPage();
  }

  function selectLink(name: string) {
    selectedLinkName.value = name;
  }

  function selectReadStatus(status: LinkFeedReadStatus) {
    selectedReadStatus.value = status;
  }

  async function markItemRead(item: LinkFeedItem, read: boolean) {
    if (!item.id || readMutation.isPending.value) {
      return false;
    }
    try {
      await readMutation.mutateAsync({ id: item.id, read });
      return true;
    } catch {
      return false;
    }
  }

  async function markItemFavorite(item: LinkFeedItem, favorite: boolean) {
    if (!item.id || favoriteMutation.isPending.value) {
      return false;
    }
    try {
      await favoriteMutation.mutateAsync({ id: item.id, favorite });
      return true;
    } catch {
      return false;
    }
  }

  async function markItemReadLater(item: LinkFeedItem, readLater: boolean) {
    if (!item.id || readLaterMutation.isPending.value) {
      return false;
    }
    try {
      await readLaterMutation.mutateAsync({ id: item.id, readLater });
      return true;
    } catch {
      return false;
    }
  }

  async function openItem(item: LinkFeedItem) {
    if (!item.read) {
      const markedRead = await markItemRead(item, true);
      if (!markedRead) {
        return false;
      }
    }

    if (item.readLater) {
      return await markItemReadLater(item, false);
    }
    return true;
  }

  return {
    items,
    selectedLinkName,
    selectedReadStatus,
    hasNext,
    isLoading,
    isLoadingMore,
    markingReadItemId,
    markingFavoriteItemId,
    markingReadLaterItemId,
    reload,
    fetchNextPage,
    selectLink,
    selectReadStatus,
    markItemRead,
    markItemFavorite,
    markItemReadLater,
    openItem,
  };
}

export type LinkFeedItems = ReturnType<typeof useLinkFeedItems>;
