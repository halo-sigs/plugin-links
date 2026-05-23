<script lang="ts" setup>
import type { LinkFeedItem } from "@/api/generated";
import { VButton, VLoading } from "@halo-dev/components";
import LinkFeedItemCard from "./LinkFeedItemCard.vue";

defineProps<{
  items: LinkFeedItem[];
  sourceName: (linkName?: string) => string;
  publishedAtText: (item: LinkFeedItem) => string;
  emptyText: string;
  hasNext?: boolean;
  isLoading?: boolean;
  isLoadingMore?: boolean;
  markingReadItemId?: string;
  markingFavoriteItemId?: string;
  markingReadLaterItemId?: string;
  compact?: boolean;
  scrollable?: boolean;
}>();

const emit = defineEmits<{
  (event: "open", item: LinkFeedItem): void;
  (event: "toggleFavorite", item: LinkFeedItem, favorite: boolean): void;
  (event: "toggleReadLater", item: LinkFeedItem, readLater: boolean): void;
  (event: "toggleRead", item: LinkFeedItem, read: boolean): void;
  (event: "loadMore"): void;
}>();
</script>

<template>
  <VLoading v-if="isLoading && !items.length" />

  <div v-else-if="!items.length" class=":uno: border border-gray-100 rounded-lg bg-white px-4 py-10 text-center text-sm text-gray-500">
    {{ emptyText }}
  </div>

  <div v-else class=":uno: space-y-3" :class="{ ':uno: max-h-[65vh] overflow-auto pr-1': scrollable }">
    <LinkFeedItemCard
      v-for="item in items"
      :key="item.id"
      :compact="compact"
      :item="item"
      :source-name="sourceName(item.linkName)"
      :published-at-text="publishedAtText(item)"
      :marking-read-item-id="markingReadItemId"
      :marking-favorite-item-id="markingFavoriteItemId"
      :marking-read-later-item-id="markingReadLaterItemId"
      @open="emit('open', $event)"
      @toggle-favorite="(target, favorite) => emit('toggleFavorite', target, favorite)"
      @toggle-read-later="(target, readLater) => emit('toggleReadLater', target, readLater)"
      @toggle-read="(target, read) => emit('toggleRead', target, read)"
    />

    <div v-if="hasNext" class=":uno: flex justify-center pt-2">
      <VButton :loading="isLoadingMore" @click="emit('loadMore')">加载更多</VButton>
    </div>
  </div>
</template>
