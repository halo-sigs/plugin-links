import { linksConsoleApiClient } from "@/api";
import type { LinkFeedItemPage } from "@/api/generated";
import { useInfiniteQuery, useQueryClient } from "@tanstack/vue-query";
import { useLocalStorage } from "@vueuse/core";
import { useRouteQuery } from "@vueuse/router";
import { computed, toValue, type MaybeRefOrGetter } from "vue";

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

export function useLinkFeedItems(options: UseLinkFeedItemsOptions = {}) {
  const queryClient = useQueryClient();
  const { autoLoad = true } = options;
  const selectedLinkName = useRouteQuery("link", "");
  const selectedReadStatus = useLocalStorage<LinkFeedReadStatus>("plugin:links:selected-read-status", "");
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
  const isFetching = computed(() => query.isFetching.value);
  const isLoadingMore = computed(() => query.isFetchingNextPage.value);

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

  return {
    items,
    selectedLinkName,
    selectedReadStatus,
    hasNext,
    isLoading,
    isFetching,
    isLoadingMore,
    reload,
    fetchNextPage,
    selectLink,
    selectReadStatus,
  };
}

export type LinkFeedItems = ReturnType<typeof useLinkFeedItems>;
