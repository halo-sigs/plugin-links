<script lang="ts" setup>
import type { LinkFeedItems } from "@/composables/use-link-feed";
import { VButton, VLoading } from "@halo-dev/components";
import { useIntersectionObserver } from "@vueuse/core";
import { computed, shallowRef, useTemplateRef, watch } from "vue";
import LinkFeedItemCard from "./LinkFeedItemCard.vue";

const props = defineProps<{
  feed: LinkFeedItems;
  sourceName: (linkName?: string) => string;
  emptyText: string;
  compact?: boolean;
  scrollable?: boolean;
}>();

const loadMoreTrigger = useTemplateRef<HTMLElement>("loadMoreTrigger");
const isLoadMoreTriggerVisible = shallowRef(false);
const items = computed(() => props.feed.items.value);
const hasNext = computed(() => props.feed.hasNext.value);
const isLoading = computed(() => props.feed.isLoading.value);
const isLoadingMore = computed(() => props.feed.isLoadingMore.value);

const { isSupported: isIntersectionObserverSupported } = useIntersectionObserver(
  loadMoreTrigger,
  ([entry]) => {
    isLoadMoreTriggerVisible.value = !!entry?.isIntersecting;
  },
  {
    rootMargin: "160px 0px",
  },
);

watch([isLoadMoreTriggerVisible, hasNext, isLoading, isLoadingMore], () => {
  if (!isLoadMoreTriggerVisible.value || !hasNext.value || isLoading.value || isLoadingMore.value) {
    return;
  }
  props.feed.fetchNextPage();
});
</script>

<template>
  <VLoading v-if="isLoading && !items.length" />

  <div
    v-else-if="!items.length"
    class=":uno: border border-gray-100 rounded-lg bg-white px-4 py-10 text-center text-sm text-gray-500"
  >
    {{ emptyText }}
  </div>

  <div v-else class=":uno: space-y-3" :class="{ ':uno: max-h-[65vh] overflow-auto pr-1': scrollable }">
    <LinkFeedItemCard
      v-for="item in items"
      :key="item.id"
      :compact="compact"
      :item="item"
      :source-name="sourceName(item.linkName)"
    />

    <div v-if="hasNext || isLoadingMore" ref="loadMoreTrigger" class=":uno: min-h-10 flex justify-center pt-2">
      <span v-if="isLoadingMore" class=":uno: text-xs text-gray-500" role="status">加载中...</span>
      <VButton v-else-if="hasNext && !isIntersectionObserverSupported" @click="feed.fetchNextPage()">加载更多</VButton>
    </div>
  </div>
</template>
