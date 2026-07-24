<script lang="ts" setup>
import { linksCoreApiClient } from "@/api";
import type { JsonPatchInner, Link, LinkGroupVo } from "@/api/generated";
import {
  accessVerificationStatusMeta,
  backlinkVerificationStatusMeta,
  type LinkVerificationTone,
} from "@/composables/link-verification-status";
import { QK_LINK_GROUPS } from "@/composables/use-group-fetch";
import { QK_GROUPS_WITH_LINKS, QK_RSS_GROUPS_WITH_LINKS } from "@/composables/use-link-fetch";
import { Dialog, IconExternalLinkLine, Toast } from "@halo-dev/components";
import { utils } from "@halo-dev/ui-shared";
import ContextMenu, { type MenuItem } from "@imengyu/vue3-context-menu";
import "@imengyu/vue3-context-menu/lib/vue3-context-menu.css";
import { useQueryClient } from "@tanstack/vue-query";
import { computed, h } from "vue";
import Delete2LineIcon from "~icons/mingcute/delete-2-line";
import MingcuteEdit4Line from "~icons/mingcute/edit-4-line";
import MingcuteMoveLine from "~icons/mingcute/move-line";
import Rss2FillIcon from "~icons/mingcute/rss-2-fill";
import RiLinkM from "~icons/ri/link-m";
import RiPulseLine from "~icons/ri/pulse-line";

const props = defineProps<{
  selectMode?: boolean;
  sortMode?: boolean;
  link: Link;
  groupName?: string;
  groups?: LinkGroupVo[];
}>();

interface LinkBadgeStatusItem {
  key: "access" | "backlink" | "rss";
  label: string;
  tone: LinkVerificationTone;
  tooltip: string;
}

const emit = defineEmits<{
  (event: "open-edit"): void;
}>();

const queryClient = useQueryClient();

function handleClick() {
  if (!props.selectMode && !props.sortMode) {
    emit("open-edit");
  }
}

function handleContextMenu(event: MouseEvent) {
  if (!props.selectMode && !props.sortMode) {
    const moveItems: MenuItem[] = moveTargetGroups.value.map((group) => ({
      label: group.spec?.displayName || group.metadata.name,
      onClick: () => handleMove(group),
    }));

    if (props.groupName) {
      moveItems.push({
        label: "未分组",
        divided: moveItems.length ? "up" : false,
        onClick: () => handleMove(),
      });
    }

    ContextMenu.showContextMenu({
      x: event.x,
      y: event.y,
      theme: "mac",
      zIndex: 999,
      items: [
        {
          label: "编辑链接",
          onClick: () => emit("open-edit"),
          icon: h(MingcuteEdit4Line),
        },
        {
          label: "移动到",
          disabled: !moveItems.length,
          children: moveItems,
          icon: h(MingcuteMoveLine),
        },
        {
          label: "删除链接",
          divided: "up",
          icon: h(Delete2LineIcon),
          customClass: ":uno: !text-red-500",
          onClick: handleDelete,
        },
      ],
    });
  }
}

const displayName = computed(() => props.link.spec?.displayName || props.link.metadata.name);
const linkUrl = computed(() => props.link.spec?.url || "");
const moveTargetGroups = computed(() => props.groups?.filter((group) => group.metadata.name !== props.groupName) || []);

const rssStatusTone = computed<LinkVerificationTone>(() => {
  if (hasPartialRssFailure(props.link)) {
    return "warning";
  }
  if (hasRssFailure(props.link)) {
    return "danger";
  }
  if (props.link.status?.rss?.lastSuccessAt) {
    return "success";
  }
  return "warning";
});

const rssTooltip = computed(() => {
  const rss = props.link.status?.rss;
  const feedCount = rssFeedUrls(props.link).length;
  const feedCountText = feedCount > 1 ? `${feedCount} 个订阅源，` : "";
  if (hasPartialRssFailure(props.link)) {
    return `RSS 部分订阅源获取失败，${feedCountText}缓存 ${rss?.itemCount || 0} 篇`;
  }
  if (hasRssFailure(props.link) && rss?.lastError) {
    return `RSS 获取失败：${rss.lastError}`;
  }
  if (rss?.lastSuccessAt) {
    return `RSS 已启用，${feedCountText}缓存 ${rss.itemCount || 0} 篇`;
  }
  return "RSS 已启用，等待获取";
});

const accessStatusMeta = computed(() => accessVerificationStatusMeta(props.link));
const backlinkStatusMeta = computed(() => backlinkVerificationStatusMeta(props.link));

const accessStatusLabel = computed(() => {
  switch (props.link.status?.verification?.access?.state) {
    case "CHECKING":
      return "检测中";
    case "ACCESSIBLE":
      return "正常";
    case "INACCESSIBLE":
      return "异常";
    default:
      return "待检";
  }
});

const accessTooltip = computed(() => {
  return verificationTooltip(accessStatusMeta.value, props.link.status?.verification?.access?.checkedAt);
});

const backlinkTooltip = computed(() => {
  return verificationTooltip(backlinkStatusMeta.value, props.link.status?.verification?.backlink?.checkedAt);
});

const statusItems = computed<LinkBadgeStatusItem[]>(() => {
  const items: LinkBadgeStatusItem[] = [];

  items.push({
    key: "access",
    label: accessStatusLabel.value,
    tone: accessStatusMeta.value.tone,
    tooltip: accessTooltip.value,
  });

  if (props.link.spec.verification?.backlinkScanUrl) {
    items.push({
      key: "backlink",
      label: "反链",
      tone: backlinkStatusMeta.value.tone,
      tooltip: backlinkTooltip.value,
    });
  }

  if (props.link.spec?.rss?.enabled) {
    items.push({
      key: "rss",
      label: "RSS",
      tone: rssStatusTone.value,
      tooltip: rssTooltip.value,
    });
  }

  return items;
});

const showStatusDock = computed(() => !props.selectMode && !props.sortMode && statusItems.value.length > 0);
const showExternalLink = computed(() => !props.selectMode && !props.sortMode && !!linkUrl.value);

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

function verificationTooltip(meta: ReturnType<typeof accessVerificationStatusMeta>, checkedAt?: string) {
  if (!checkedAt) {
    return `${meta.label}：${meta.description}`;
  }
  return `${meta.label}：${meta.description}，${utils.date.format(checkedAt)}`;
}

function handleMove(group?: LinkGroupVo) {
  const targetName = group?.spec?.displayName || "未分组";

  Dialog.warning({
    title: "移动链接",
    description: `确认将 ${displayName.value} 移动到${targetName}吗？`,
    confirmType: "danger",
    onConfirm: () => moveLink(group?.metadata.name),
  });
}

async function moveLink(groupName?: string) {
  const jsonPatchInner: JsonPatchInner[] = groupName
    ? [{ op: "add", path: "/spec/groupName", value: groupName }]
    : [{ op: "remove", path: "/spec/groupName" }];

  await linksCoreApiClient.link.patchLink({
    name: props.link.metadata.name,
    jsonPatchInner,
  });

  Toast.success("移动成功");
  invalidateLinkQueries();
}

function handleDelete() {
  Dialog.warning({
    title: "是否确认删除当前的链接？",
    description: "删除之后将无法恢复。",
    confirmType: "danger",
    onConfirm: async () => {
      await linksCoreApiClient.link.deleteLink({
        name: props.link.metadata.name,
      });

      Toast.success("删除成功");
      invalidateLinkQueries();
    },
  });
}

function invalidateLinkQueries() {
  queryClient.invalidateQueries({ queryKey: [QK_LINK_GROUPS] });
  queryClient.invalidateQueries({ queryKey: [QK_GROUPS_WITH_LINKS] });
  queryClient.invalidateQueries({ queryKey: [QK_RSS_GROUPS_WITH_LINKS] });
}
</script>
<template>
  <label
    class=":uno: link-badge min-w-0 w-full cursor-pointer"
    :class="{
      ':uno: animate-flash opacity-50': link.metadata.deletionTimestamp,
      ':uno: cursor-move': sortMode,
      ':uno: link-badge--plain': !showStatusDock,
    }"
    @click="handleClick"
    @contextmenu.prevent="handleContextMenu"
  >
    <div class=":uno: link-badge__main">
      <span v-if="selectMode" class=":uno: link-badge__media">
        <slot name="checkbox"></slot>
      </span>
      <img v-else :src="link.spec?.logo" class=":uno: link-badge__logo" />

      <div class=":uno: link-badge__content">
        <span
          class=":uno: link-badge__title"
          :class="{
            ':uno: line-through': link.metadata.deletionTimestamp,
          }"
          v-tooltip="{
            content: displayName,
            disabled: selectMode || sortMode,
          }"
        >
          {{ displayName }}
        </span>
        <span
          class=":uno: link-badge__url"
          v-tooltip="{
            content: linkUrl,
            disabled: selectMode || sortMode,
          }"
        >
          {{ linkUrl }}
        </span>
      </div>
    </div>

    <div v-if="showStatusDock" class=":uno: link-badge__status-dock" aria-label="链接状态">
      <span
        v-for="item in statusItems"
        :key="item.key"
        v-tooltip="{
          content: item.tooltip,
        }"
        class=":uno: link-badge__status-pill"
        :class="`:uno: link-badge__status-pill--${item.tone}`"
        :aria-label="item.tooltip"
        role="img"
      >
        <Rss2FillIcon v-if="item.key === 'rss'" class=":uno: link-badge__status-icon" />
        <RiPulseLine v-else-if="item.key === 'access'" class=":uno: link-badge__status-icon" />
        <RiLinkM v-else class=":uno: link-badge__status-icon" />
        <span class=":uno: link-badge__status-label">{{ item.label }}</span>
      </span>
      <a
        v-if="showExternalLink"
        :href="linkUrl"
        target="_blank"
        rel="noreferrer"
        class=":uno: link-badge__external-link"
        :aria-label="`打开 ${displayName}`"
        @click.stop
      >
        <IconExternalLinkLine class=":uno: link-badge__external-icon" />
        <span class=":uno: link-badge__status-label">访问</span>
      </a>
    </div>
  </label>
</template>

<style scoped>
.link-badge {
  display: flex;
  min-height: 4.625rem;
  flex-direction: column;
  gap: 0.4375rem;
  overflow: hidden;
  border: 1px solid rgb(229 231 235);
  border-radius: 0.5rem;
  background: rgb(249 250 251);
  padding: 0.5625rem;
  transition:
    background-color 0.18s ease,
    border-color 0.18s ease;
}

.link-badge:hover,
.link-badge:focus-within {
  border-color: rgb(209 213 219);
  background: rgb(243 244 246);
}

.link-badge--plain {
  min-height: 3.5rem;
  justify-content: center;
}

.link-badge__main {
  display: grid;
  min-width: 0;
  grid-template-columns: 1.875rem minmax(0, 1fr);
  align-items: center;
  gap: 0.625rem;
}

.link-badge__media {
  display: inline-flex;
  width: 1.875rem;
  height: 1.875rem;
  align-items: center;
  justify-content: center;
}

.link-badge__logo {
  width: 1.875rem;
  height: 1.875rem;
  flex: none;
  border-radius: 0.375rem;
  background: rgb(255 255 255);
  object-fit: cover;
  box-shadow:
    0 0 0 1px rgb(15 23 42 / 0.08),
    0 1px 2px rgb(15 23 42 / 0.06);
}

.link-badge__content {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0.0625rem;
}

.link-badge__title,
.link-badge__url {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.link-badge__title {
  color: rgb(17 24 39);
  font-size: 0.8125rem;
  font-weight: 500;
  line-height: 1.125rem;
}

.link-badge__url {
  color: rgb(107 114 128);
  font-size: 0.75rem;
  line-height: 1rem;
}

.link-badge__external-link {
  display: inline-flex;
  height: 1.125rem;
  flex: none;
  align-items: center;
  justify-content: center;
  gap: 0.1875rem;
  overflow: hidden;
  color: rgb(107 114 128);
  font-size: 0.625rem;
  font-weight: 500;
  line-height: 1;
  text-decoration: none;
  white-space: nowrap;
  opacity: 0.82;
  transition:
    color 0.18s ease,
    opacity 0.18s ease;
}

.link-badge__external-link:hover,
.link-badge__external-link:focus-visible {
  color: rgb(107 114 128);
  opacity: 1;
}

.link-badge__external-icon {
  width: 0.75rem;
  height: 0.75rem;
  flex: none;
}

.link-badge__status-dock {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  justify-content: flex-start;
  gap: 0.4375rem;
}

.link-badge__status-pill {
  --status-color: rgb(107 114 128);
  display: inline-flex;
  min-width: 0;
  height: 1.125rem;
  flex: none;
  align-items: center;
  justify-content: center;
  gap: 0.1875rem;
  overflow: hidden;
  color: rgb(107 114 128);
  font-size: 0.625rem;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
}

.link-badge__status-icon {
  width: 0.75rem;
  height: 0.75rem;
  flex: none;
  color: var(--status-color);
  opacity: 0.82;
}

.link-badge__status-label {
  overflow: hidden;
  text-overflow: ellipsis;
}

.link-badge__status-pill--success {
  --status-color: rgb(22 163 74);
}

.link-badge__status-pill--warning {
  --status-color: rgb(202 138 4);
}

.link-badge__status-pill--danger {
  --status-color: rgb(220 38 38);
}

.link-badge__status-pill--muted {
  --status-color: rgb(148 163 184);
}
</style>
