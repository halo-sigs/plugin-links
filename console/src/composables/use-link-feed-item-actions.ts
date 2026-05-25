import { linksConsoleApiClient } from "@/api";
import type { LinkFeedItem } from "@/api/generated";
import { QK_LINK_FEED_ITEMS } from "@/composables/use-link-feed";
import { invalidateLinkFeedUnreadSummary } from "@/composables/use-link-feed-unread-summary";
import { useMutation, useQueryClient } from "@tanstack/vue-query";
import { computed, toValue, type MaybeRefOrGetter } from "vue";

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

export function useLinkFeedItemActions(item: MaybeRefOrGetter<LinkFeedItem | undefined>) {
  const queryClient = useQueryClient();

  const readMutation = useMutation<void, unknown, MarkReadVariables>({
    mutationFn: async ({ id, read }) => {
      await linksConsoleApiClient.feed.markLinkFeedItemRead({ id, read });
    },
    onSuccess: invalidateFeedReadState,
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

  const isMarkingRead = computed(() => readMutation.isPending.value);
  const isMarkingFavorite = computed(() => favoriteMutation.isPending.value);
  const isMarkingReadLater = computed(() => readLaterMutation.isPending.value);

  async function invalidateFeedItems() {
    await queryClient.invalidateQueries({ queryKey: [QK_LINK_FEED_ITEMS] });
  }

  async function invalidateFeedReadState() {
    await Promise.all([invalidateFeedItems(), invalidateLinkFeedUnreadSummary(queryClient)]);
  }

  async function markRead(read: boolean) {
    const target = toValue(item);
    if (!target?.id || readMutation.isPending.value) {
      return false;
    }
    return await markReadById(target.id, read);
  }

  async function markFavorite(favorite: boolean) {
    const target = toValue(item);
    if (!target?.id || favoriteMutation.isPending.value) {
      return false;
    }
    return await markFavoriteById(target.id, favorite);
  }

  async function markReadLater(readLater: boolean) {
    const target = toValue(item);
    if (!target?.id || readLaterMutation.isPending.value) {
      return false;
    }
    return await markReadLaterById(target.id, readLater);
  }

  async function toggleRead() {
    const target = toValue(item);
    if (!target?.id) {
      return false;
    }
    return await markReadById(target.id, !target.read);
  }

  async function toggleFavorite() {
    const target = toValue(item);
    if (!target?.id) {
      return false;
    }
    return await markFavoriteById(target.id, !target.favorite);
  }

  async function toggleReadLater() {
    const target = toValue(item);
    if (!target?.id) {
      return false;
    }
    return await markReadLaterById(target.id, !target.readLater);
  }

  async function openItem() {
    const target = toValue(item);
    if (!target?.id) {
      return false;
    }

    if (!target.read) {
      const markedRead = await markReadById(target.id, true);
      if (!markedRead) {
        return false;
      }
    }

    if (target.readLater) {
      return await markReadLaterById(target.id, false);
    }
    return true;
  }

  async function markReadById(id: string, read: boolean) {
    if (readMutation.isPending.value) {
      return false;
    }
    try {
      await readMutation.mutateAsync({ id, read });
      return true;
    } catch {
      return false;
    }
  }

  async function markFavoriteById(id: string, favorite: boolean) {
    if (favoriteMutation.isPending.value) {
      return false;
    }
    try {
      await favoriteMutation.mutateAsync({ id, favorite });
      return true;
    } catch {
      return false;
    }
  }

  async function markReadLaterById(id: string, readLater: boolean) {
    if (readLaterMutation.isPending.value) {
      return false;
    }
    try {
      await readLaterMutation.mutateAsync({ id, readLater });
      return true;
    } catch {
      return false;
    }
  }

  return {
    isMarkingRead,
    isMarkingFavorite,
    isMarkingReadLater,
    markRead,
    markFavorite,
    markReadLater,
    toggleRead,
    toggleFavorite,
    toggleReadLater,
    openItem,
  };
}
