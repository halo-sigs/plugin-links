<script lang="ts" setup>
import type { Link } from "@/api/generated";
import { IconExternalLinkLine } from "@halo-dev/components";
import { computed } from "vue";
import MdiRss from "~icons/mdi/rss";

const props = defineProps<{
  selectMode?: boolean;
  sortMode?: boolean;
  link: Link;
}>();

const emit = defineEmits<{
  (event: "open-edit"): void;
}>();

function handleClick() {
  if (!props.selectMode && !props.sortMode) {
    emit("open-edit");
  }
}

const rssStatusClass = computed(() => {
  if (hasPartialRssFailure(props.link)) {
    return ":uno: bg-amber-100 text-amber-600";
  }
  if (hasRssFailure(props.link)) {
    return ":uno: bg-red-100 text-red-600";
  }
  if (props.link.status?.rss?.lastSuccessAt) {
    return ":uno: bg-emerald-100 text-emerald-600";
  }
  return ":uno: bg-amber-100 text-amber-600";
});

const rssTooltip = computed(() => {
  const rss = props.link.status?.rss;
  const feedCount = rssFeedUrls(props.link).length;
  const feedCountText = feedCount > 1 ? `${feedCount} 个订阅源，` : "";
  if (hasPartialRssFailure(props.link)) {
    return `RSS 部分订阅源刷新失败，${feedCountText}缓存 ${rss?.itemCount || 0} 篇`;
  }
  if (hasRssFailure(props.link) && rss?.lastError) {
    return `RSS 刷新失败：${rss.lastError}`;
  }
  if (rss?.lastSuccessAt) {
    return `RSS 已启用，${feedCountText}缓存 ${rss.itemCount || 0} 篇`;
  }
  return "RSS 已启用，等待刷新";
});

function rssFeedUrls(link: Link) {
  return link.spec?.rss?.feedUrls?.filter((feedUrl) => !!feedUrl?.trim()) || [];
}

function hasPartialRssFailure(link: Link) {
  const feeds = link.status?.rss?.feeds || [];
  return feeds.some((feed) => !!feed.lastError) && feeds.some((feed) => !feed.lastError && feed.lastSuccessAt);
}

function hasRssFailure(link: Link) {
  const feeds = link.status?.rss?.feeds || [];
  if (feeds.length) {
    return feeds.every((feed) => !!feed.lastError);
  }
  return !!link.status?.rss?.lastError;
}
</script>
<template>
  <label
    class=":uno: min-w-0 w-full inline-flex cursor-pointer items-center gap-2.5 rounded-lg bg-gray-100 px-2 py-1 transition-colors hover:bg-gray-200"
    :class="{
      ':uno: animate-flash opacity-50': link.metadata.deletionTimestamp,
      ':uno: cursor-move': sortMode,
    }"
    @click="handleClick"
  >
    <slot name="checkbox" v-if="selectMode"></slot>
    <img v-else :src="link.spec?.logo" class=":uno: size-4 flex-none shrink-0 rounded-sm" />
    <div class=":uno: min-w-0 flex flex-1 shrink flex-col">
      <span
        class=":uno: truncate text-xs text-gray-900"
        :class="{
          ':uno: line-through': link.metadata.deletionTimestamp,
        }"
        v-tooltip="{
          content: link.spec?.displayName,
          disabled: selectMode || sortMode,
        }"
      >
        {{ link.spec?.displayName }}
      </span>
      <span
        class=":uno: truncate text-xs text-gray-500"
        v-tooltip="{
          content: link.spec?.url,
          disabled: selectMode || sortMode,
        }"
      >
        {{ link.spec?.url }}
      </span>
    </div>
    <span
      v-if="!selectMode && !sortMode && link.spec?.rss?.enabled"
      v-tooltip="{
        content: rssTooltip,
      }"
      class=":uno: size-5 flex flex-none items-center justify-center rounded"
      :class="rssStatusClass"
    >
      <MdiRss class=":uno: size-3.5" />
    </span>
    <a
      v-if="!selectMode && !sortMode && link.spec?.url"
      :href="link.spec.url"
      target="_blank"
      class=":uno: flex-none opacity-40 transition-opacity hover:opacity-100"
      @click.stop
    >
      <IconExternalLinkLine class=":uno: size-3.5 text-gray-500" />
    </a>
  </label>
</template>
