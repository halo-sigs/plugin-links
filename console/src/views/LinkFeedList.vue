<script lang="ts" setup>
import type { Link, LinkFeedItem, LinkGroup } from "@/api/generated";
import MdiRss from "~icons/mdi/rss";
import { useLinksFetch } from "@/composables/use-link-fetch";
import { useLinkFeedItems } from "@/composables/use-link-feed";
import { IconArrowLeft, IconExternalLinkLine, VButton, VLoading, VPageHeader, VSpace } from "@halo-dev/components";
import { computed } from "vue";
import { useRouter } from "vue-router";

const router = useRouter();
const { data: groupsWithLinks } = useLinksFetch();
const {
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
} = useLinkFeedItems();

const allLinks = computed(() => {
  return groupsWithLinks.value?.flatMap((group) => group.links) || [];
});

const linkByName = computed(() => {
  return new Map(allLinks.value.map((link) => [link.metadata.name, link]));
});

const groups = computed(() => {
  return groupsWithLinks.value?.map((item) => item.group).filter((group): group is LinkGroup => !!group) || [];
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
      <VSpace>
        <VButton size="sm" :loading="isLoading" @click="load()">刷新列表</VButton>
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
      >
        <div class=":uno: flex flex-wrap items-start justify-between gap-3">
          <div class=":uno: min-w-0 flex-1">
            <a
              v-if="item.url"
              :href="item.url"
              target="_blank"
              rel="noopener noreferrer"
              class=":uno: hover:text-primary max-w-full inline-flex items-center gap-1 text-sm text-gray-900 font-medium"
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
            </div>
          </div>
          <VButton
            v-if="item.linkName"
            size="sm"
            ghost
            :loading="refreshingLinkName === item.linkName"
            @click="refreshLink(item.linkName)"
          >
            刷新
          </VButton>
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
