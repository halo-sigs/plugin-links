<script lang="ts" setup>
import type { Link } from "@/api/generated";
import { computed } from "vue";
import MdiRss from "~icons/mdi/rss";

const props = defineProps<{
  links: Link[];
  selectedLinkName: string;
  loading?: boolean;
}>();

const emit = defineEmits<{
  (event: "selectLink", name: string): void;
}>();

const totalItemCount = computed(() => {
  return props.links.reduce((total, link) => total + (link.status?.rss?.itemCount || 0), 0);
});

function linkTitle(link: Link) {
  return link.spec?.displayName || link.metadata.name;
}

function rssStatusClass(link: Link) {
  if (link.status?.rss?.lastError) {
    return ":uno: bg-red-100 text-red-600";
  }
  if (link.status?.rss?.lastSuccessAt) {
    return ":uno: bg-emerald-100 text-emerald-600";
  }
  return ":uno: bg-amber-100 text-amber-600";
}

function rssTooltip(link: Link) {
  const rss = link.status?.rss;
  if (rss?.lastError) {
    return `RSS 刷新失败：${rss.lastError}`;
  }
  if (rss?.lastSuccessAt) {
    return `RSS 已启用，缓存 ${rss.itemCount || 0} 篇`;
  }
  return "RSS 已启用，等待刷新";
}

function itemCountText(count?: number) {
  if (!count) {
    return "";
  }
  return count > 999 ? "999+" : `${count}`;
}
</script>

<template>
  <aside
    class=":uno: min-w-0 border border-gray-100 rounded-lg bg-white p-3 lg:sticky lg:top-4 lg:max-h-[calc(100vh-2rem)] lg:w-72 lg:flex-none lg:self-start lg:overflow-hidden"
  >
    <div class=":uno: mb-3 flex items-center justify-between gap-3">
      <div class=":uno: text-sm text-gray-900 font-semibold">订阅</div>
      <span class=":uno: rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-500">{{ links.length }}</span>
    </div>

    <div v-if="loading && !links.length" class=":uno: py-6 text-center text-xs text-gray-500">加载订阅中...</div>

    <div
      v-else
      class=":uno: flex gap-2 overflow-x-auto pb-1 lg:max-h-[calc(100vh-6.5rem)] lg:flex-col lg:overflow-x-hidden lg:overflow-y-auto lg:pb-0"
    >
      <button
        type="button"
        class=":uno: min-w-42 flex flex-none items-center gap-2 border rounded-md px-3 py-2 text-left transition-colors lg:w-full"
        :class="{
          ':uno: border-primary bg-primary/5 text-primary': !selectedLinkName,
          ':uno: border-transparent bg-gray-50 text-gray-700 hover:bg-gray-100': selectedLinkName,
        }"
        @click="emit('selectLink', '')"
      >
        <span class=":uno: size-7 flex flex-none items-center justify-center rounded bg-white text-gray-500">
          <MdiRss class=":uno: size-4" />
        </span>
        <span class=":uno: min-w-0 flex-1">
          <span class=":uno: block truncate text-sm font-medium">全部动态</span>
          <span class=":uno: block truncate text-xs text-gray-500">所有订阅源</span>
        </span>
        <span v-if="totalItemCount" class=":uno: flex-none text-xs text-gray-400">
          {{ itemCountText(totalItemCount) }}
        </span>
      </button>

      <button
        v-for="link in links"
        :key="link.metadata.name"
        type="button"
        class=":uno: min-w-56 flex flex-none items-center gap-2 border rounded-md px-3 py-2 text-left transition-colors lg:w-full"
        :class="{
          ':uno: border-primary bg-primary/5 text-primary': selectedLinkName === link.metadata.name,
          ':uno: border-transparent bg-gray-50 text-gray-700 hover:bg-gray-100':
            selectedLinkName !== link.metadata.name,
        }"
        @click="emit('selectLink', link.metadata.name)"
      >
        <img
          v-if="link.spec?.logo"
          :src="link.spec.logo"
          class=":uno: size-7 flex-none rounded bg-white object-cover"
          alt=""
        />
        <span v-else class=":uno: size-7 flex flex-none items-center justify-center rounded bg-white text-gray-400">
          <MdiRss class=":uno: size-4" />
        </span>
        <span class=":uno: min-w-0 flex-1">
          <span class=":uno: block truncate text-sm font-medium">{{ linkTitle(link) }}</span>
          <span class=":uno: block truncate text-xs text-gray-500">{{
            link.spec?.url || link.spec?.rss?.feedUrl
          }}</span>
        </span>
        <span
          v-tooltip="{
            content: rssTooltip(link),
          }"
          class=":uno: size-5 flex flex-none items-center justify-center rounded"
          :class="rssStatusClass(link)"
        >
          <MdiRss class=":uno: size-3.5" />
        </span>
        <span v-if="link.status?.rss?.itemCount" class=":uno: flex-none text-xs text-gray-400">
          {{ itemCountText(link.status.rss.itemCount) }}
        </span>
      </button>

      <div v-if="!links.length" class=":uno: px-3 py-8 text-center text-sm text-gray-500 lg:px-0">暂无已订阅链接</div>
    </div>
  </aside>
</template>
