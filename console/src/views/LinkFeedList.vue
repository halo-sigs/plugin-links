<script lang="ts" setup>
import type { Link, LinkFeedItem, LinkGroup } from "@/api/generated";
import { useRssLinksFetch } from "@/composables/use-link-fetch";
import {
  useLinkFeedItems,
  type LinkFeedBooleanStatus,
  type LinkFeedReadStatus,
} from "@/composables/use-link-feed";
import { IconArrowLeft, IconExternalLinkLine, VButton, VLoading, VPageHeader, VSpace } from "@halo-dev/components";
import { computed } from "vue";
import { useRouter } from "vue-router";
import MdiClockCheckOutline from "~icons/mdi/clock-check-outline";
import MdiClockOutline from "~icons/mdi/clock-outline";
import MdiRefresh from "~icons/mdi/refresh";
import MdiRss from "~icons/mdi/rss";
import MdiStar from "~icons/mdi/star";
import MdiStarOutline from "~icons/mdi/star-outline";

const router = useRouter();
const { data: groupsWithLinks } = useRssLinksFetch();
const {
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
} = useLinkFeedItems();

const allLinks = computed(() => {
  return groupsWithLinks.value?.flatMap((group) => group.links) || [];
});

const linkByName = computed(() => {
  return new Map(allLinks.value.map((link) => [link.metadata.name, link]));
});

const groups = computed(() => {
  return groupsWithLinks.value
    ?.filter((item) => item.links.length)
    .map((item) => item.group)
    .filter((group): group is LinkGroup => !!group) || [];
});

function sourceLink(linkName?: string): Link | undefined {
  if (!linkName) {
    return undefined;
  }
  return linkByName.value.get(linkName);
}

function sourceName(linkName?: string) {
  return sourceLink(linkName)?.spec?.displayName || linkName || "未知链接";
}

function articleTitle(item: LinkFeedItem) {
  return item.title || item.url || "未命名文章";
}

function handleReadStatusChange(event: Event) {
  selectReadStatus((event.target as HTMLSelectElement).value as LinkFeedReadStatus);
}

function handleFavoriteStatusChange(event: Event) {
  selectFavoriteStatus((event.target as HTMLSelectElement).value as LinkFeedBooleanStatus);
}

function handleReadLaterStatusChange(event: Event) {
  selectReadLaterStatus((event.target as HTMLSelectElement).value as LinkFeedBooleanStatus);
}

function formatTime(value?: string) {
  if (!value) {
    return "未知时间";
  }
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}
</script>

<template>
  <VPageHeader title="友链动态">
    <template #icon>
      <MdiRss />
    </template>
    <template #actions>
      <VButton size="sm" @click="router.push({ name: 'Links' })">
        <template #icon>
          <IconArrowLeft class=":uno: size-full" />
        </template>
        返回链接
      </VButton>
    </template>
  </VPageHeader>

  <div class=":uno: p-4">
    <div class=":uno: mb-4 flex flex-wrap items-end gap-3 border border-gray-100 rounded-lg bg-white p-4">
      <label class=":uno: min-w-48 flex flex-col gap-1 text-xs text-gray-600">
        <span>链接</span>
        <select
          v-model="selectedLinkName"
          class=":uno: h-9 border border-gray-200 rounded bg-white px-2 text-sm text-gray-900"
          @change="selectLink(selectedLinkName)"
        >
          <option value="">全部链接</option>
          <option v-for="link in allLinks" :key="link.metadata.name" :value="link.metadata.name">
            {{ link.spec?.displayName || link.metadata.name }}
          </option>
        </select>
      </label>
      <label class=":uno: min-w-48 flex flex-col gap-1 text-xs text-gray-600">
        <span>分组</span>
        <select
          v-model="selectedGroupName"
          class=":uno: h-9 border border-gray-200 rounded bg-white px-2 text-sm text-gray-900"
          @change="selectGroup(selectedGroupName)"
        >
          <option value="">全部分组</option>
          <option v-for="group in groups" :key="group.metadata.name" :value="group.metadata.name">
            {{ group.spec?.displayName || group.metadata.name }}
          </option>
        </select>
      </label>
      <label class=":uno: min-w-36 flex flex-col gap-1 text-xs text-gray-600">
        <span>阅读状态</span>
        <select
          v-model="selectedReadStatus"
          class=":uno: h-9 border border-gray-200 rounded bg-white px-2 text-sm text-gray-900"
          @change="handleReadStatusChange"
        >
          <option value="">全部</option>
          <option value="unread">未读</option>
          <option value="read">已读</option>
        </select>
      </label>
      <label class=":uno: min-w-36 flex flex-col gap-1 text-xs text-gray-600">
        <span>收藏状态</span>
        <select
          v-model="selectedFavoriteStatus"
          class=":uno: h-9 border border-gray-200 rounded bg-white px-2 text-sm text-gray-900"
          @change="handleFavoriteStatusChange"
        >
          <option value="">全部</option>
          <option value="true">已收藏</option>
          <option value="false">未收藏</option>
        </select>
      </label>
      <label class=":uno: min-w-36 flex flex-col gap-1 text-xs text-gray-600">
        <span>稍后阅读</span>
        <select
          v-model="selectedReadLaterStatus"
          class=":uno: h-9 border border-gray-200 rounded bg-white px-2 text-sm text-gray-900"
          @change="handleReadLaterStatusChange"
        >
          <option value="">全部</option>
          <option value="true">稍后阅读</option>
          <option value="false">非稍后阅读</option>
        </select>
      </label>
      <VSpace>
        <VButton size="sm" :loading="isLoading" @click="load()">
          <template #icon>
            <MdiRefresh class=":uno: size-full" />
          </template>
          重新加载
        </VButton>
      </VSpace>
    </div>

    <VLoading v-if="isLoading && !items.length" />

    <div v-else-if="!items.length" class=":uno: border border-gray-100 rounded-lg bg-white px-4 py-10 text-center text-sm text-gray-500">
      暂无友链动态
    </div>

    <div v-else class=":uno: space-y-3">
      <article
        v-for="item in items"
        :key="item.id"
        class=":uno: border border-gray-100 rounded-lg bg-white p-4 shadow-sm"
        :class="{
          ':uno: bg-gray-50/80': item.read,
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
              @click="openItem(item)"
            >
              <span class=":uno: truncate">{{ articleTitle(item) }}</span>
              <IconExternalLinkLine class=":uno: size-3.5 flex-none" />
            </a>
            <div v-else class=":uno: truncate text-sm text-gray-900 font-medium">
              {{ articleTitle(item) }}
            </div>
            <div class=":uno: mt-1 flex flex-wrap items-center gap-2 text-xs text-gray-500">
              <span>{{ sourceName(item.linkName) }}</span>
              <span>{{ formatTime(item.publishedAt || item.updatedAt || item.fetchedAt) }}</span>
              <span
                class=":uno: rounded bg-gray-100 px-1.5 py-0.5"
                :class="{
                  ':uno: bg-green-50 text-green-700': !item.read,
                }"
              >
                {{ item.read ? "已读" : "未读" }}
              </span>
              <span v-if="item.favorite" class=":uno: rounded bg-yellow-50 px-1.5 py-0.5 text-yellow-700">
                已收藏
              </span>
              <span v-if="item.readLater" class=":uno: rounded bg-blue-50 px-1.5 py-0.5 text-blue-700">
                稍后阅读
              </span>
            </div>
          </div>
          <div class=":uno: flex flex-none flex-wrap items-center justify-end gap-2">
            <VButton
              size="sm"
              ghost
              :aria-label="item.favorite ? '取消收藏' : '收藏'"
              :loading="markingFavoriteItemId === item.id"
              v-tooltip="{
                content: item.favorite ? '取消收藏' : '收藏',
              }"
              @click="markItemFavorite(item, !item.favorite)"
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
              :loading="markingReadLaterItemId === item.id"
              v-tooltip="{
                content: item.readLater ? '移出稍后阅读' : '稍后阅读',
              }"
              @click="markItemReadLater(item, !item.readLater)"
            >
              <template #icon>
                <MdiClockCheckOutline v-if="item.readLater" class=":uno: size-full text-blue-600" />
                <MdiClockOutline v-else class=":uno: size-full" />
              </template>
            </VButton>
            <VButton
              size="sm"
              ghost
              :loading="markingReadItemId === item.id"
              @click="markItemRead(item, !item.read)"
            >
              {{ item.read ? "标为未读" : "标为已读" }}
            </VButton>
          </div>
        </div>
        <p v-if="item.summary" class=":uno: line-clamp-3 mt-3 text-sm text-gray-600">
          {{ item.summary }}
        </p>
      </article>

      <div class=":uno: flex justify-center pt-2" v-if="hasNext">
        <VButton :loading="isLoadingMore" @click="load({ append: true })">加载更多</VButton>
      </div>
    </div>
  </div>
</template>
