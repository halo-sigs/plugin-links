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

  <div v-else-if="!items.length" class=":uno: feed-empty">
    <span class=":uno: feed-empty__mark"></span>
    <span>{{ emptyText }}</span>
  </div>

  <div v-else class=":uno: feed-stream" :class="{ ':uno: feed-stream--compact': compact }">
    <LinkFeedItemCard
      v-for="item in items"
      :key="item.id"
      :compact="compact"
      :item="item"
      :source-name="sourceName(item.linkName)"
    />

    <div v-if="hasNext || isLoadingMore" ref="loadMoreTrigger" class=":uno: feed-stream__footer">
      <span v-if="isLoadingMore" class=":uno: feed-stream__loading" role="status">加载中...</span>
      <VButton v-else-if="hasNext && !isIntersectionObserverSupported" @click="feed.fetchNextPage()">加载更多</VButton>
    </div>
  </div>
</template>

<style scoped>
.feed-empty {
  display: flex;
  min-height: 13rem;
  align-items: center;
  justify-content: center;
  gap: 0.625rem;
  border: 1px dashed rgb(212 212 216);
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgb(255 255 255), rgb(250 250 250)),
    repeating-linear-gradient(135deg, rgb(24 24 27 / 0.035) 0 1px, transparent 1px 12px);
  color: rgb(113 113 122);
  font-size: 0.875rem;
  line-height: 1.25rem;
}

.feed-empty__mark {
  width: 0.5rem;
  height: 0.5rem;
  flex: none;
  border-radius: 999px;
  background: rgb(24 24 27);
  box-shadow: 0 0 0 4px rgb(24 24 27 / 0.08);
}

.feed-stream {
  overflow: hidden;
  border: 1px solid rgb(229 231 235);
  border-radius: 8px;
  background: rgb(255 255 255);
  box-shadow: 0 1px 2px rgb(15 23 42 / 0.04);
}

.feed-stream--compact {
  border-radius: 8px;
}

.feed-stream__footer {
  min-height: 3rem;
  display: flex;
  align-items: center;
  justify-content: center;
  border-top: 1px solid rgb(244 244 245);
  background: rgb(250 250 250);
  padding: 0.75rem;
}

.feed-stream__loading {
  color: rgb(113 113 122);
  font-size: 0.75rem;
  line-height: 1rem;
}
</style>
