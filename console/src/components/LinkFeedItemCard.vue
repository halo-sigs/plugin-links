<script lang="ts" setup>
import type { LinkFeedItem } from "@/api/generated";
import { useLinkFeedItemActions } from "@/composables/use-link-feed-item-actions";
import { IconExternalLinkLine, VButton } from "@halo-dev/components";
import { utils } from "@halo-dev/ui-shared";
import { computed } from "vue";
import MdiClockCheckOutline from "~icons/mdi/clock-check-outline";
import MdiClockOutline from "~icons/mdi/clock-outline";
import MdiStar from "~icons/mdi/star";
import MdiStarOutline from "~icons/mdi/star-outline";

const props = defineProps<{
  item: LinkFeedItem;
  sourceName: string;
  compact?: boolean;
}>();

function articleTitle(item: LinkFeedItem) {
  return item.title || item.url || "未命名文章";
}

const publishedAt = computed(() => {
  return props.item.publishedAt || props.item.updatedAt || props.item.fetchedAt;
});

const { isMarkingFavorite, isMarkingRead, isMarkingReadLater, openItem, toggleFavorite, toggleRead, toggleReadLater } =
  useLinkFeedItemActions(() => props.item);
</script>

<template>
  <article
    class=":uno: border border-gray-100 rounded-lg bg-white"
    :class="{
      ':uno: bg-gray-50/80': item.read,
      ':uno: p-3': compact,
      ':uno: p-4 shadow-sm': !compact,
    }"
  >
    <div class=":uno: flex flex-wrap items-start justify-between gap-3">
      <div class=":uno: min-w-0 flex-1">
        <a
          v-if="item.url"
          :href="item.url"
          target="_blank"
          rel="noopener noreferrer"
          class=":uno: hover:text-primary max-w-full inline-flex items-center gap-1 text-sm text-gray-900 font-medium"
          :class="{
            ':uno: text-gray-500': item.read,
          }"
          @click="openItem()"
        >
          <span class=":uno: truncate">{{ articleTitle(item) }}</span>
          <IconExternalLinkLine class=":uno: size-3.5 flex-none" />
        </a>
        <div v-else class=":uno: truncate text-sm text-gray-900 font-medium">
          {{ articleTitle(item) }}
        </div>
        <div class=":uno: mt-1 flex flex-wrap items-center gap-2 text-xs text-gray-500">
          <span>{{ sourceName }}</span>
          <span v-if="publishedAt" v-tooltip="utils.date.format(publishedAt)">
            {{ utils.date.timeAgo(publishedAt) }}
          </span>
          <span
            class=":uno: rounded bg-gray-100 px-1.5 py-0.5"
            :class="{
              ':uno: bg-green-50 text-green-700': !item.read,
            }"
          >
            {{ item.read ? "已读" : "未读" }}
          </span>
          <span v-if="item.favorite" class=":uno: rounded bg-yellow-50 px-1.5 py-0.5 text-yellow-700"> 已收藏 </span>
          <span v-if="item.readLater" class=":uno: rounded bg-blue-50 px-1.5 py-0.5 text-blue-700"> 稍后阅读 </span>
        </div>
      </div>
      <div class=":uno: flex flex-none flex-wrap items-center justify-end gap-2">
        <VButton
          size="sm"
          ghost
          :aria-label="item.favorite ? '取消收藏' : '收藏'"
          :loading="isMarkingFavorite"
          v-tooltip="{
            content: item.favorite ? '取消收藏' : '收藏',
          }"
          @click="toggleFavorite()"
        >
          <template #icon>
            <MdiStar v-if="item.favorite" class=":uno: size-full text-yellow-500" />
            <MdiStarOutline v-else class=":uno: size-full" />
          </template>
        </VButton>
        <VButton
          size="sm"
          ghost
          :aria-label="item.readLater ? '移出稍后阅读' : '稍后阅读'"
          :loading="isMarkingReadLater"
          v-tooltip="{
            content: item.readLater ? '移出稍后阅读' : '稍后阅读',
          }"
          @click="toggleReadLater()"
        >
          <template #icon>
            <MdiClockCheckOutline v-if="item.readLater" class=":uno: size-full text-blue-600" />
            <MdiClockOutline v-else class=":uno: size-full" />
          </template>
        </VButton>
        <VButton size="sm" ghost :loading="isMarkingRead" @click="toggleRead()">
          {{ item.read ? "标为未读" : "标为已读" }}
        </VButton>
      </div>
    </div>
    <p v-if="item.summary && !compact" class=":uno: line-clamp-3 mt-3 text-sm text-gray-600">
      {{ item.summary }}
    </p>
  </article>
</template>
