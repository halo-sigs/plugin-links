<script lang="ts" setup>
import type { Link, LinkFeedUnreadSummary } from "@/api/generated";
import { linkTitle, rssFeedUrls } from "@/composables/link-feed-status";
import { linkFeedUnreadCount } from "@/composables/use-link-feed-unread-summary";
import { IconInformation } from "@halo-dev/components";
import { computed } from "vue";
import Rss2FillIcon from "~icons/mingcute/rss-2-fill";

const props = defineProps<{
  links: Link[];
  selectedLinkName: string;
  unreadSummary?: LinkFeedUnreadSummary;
  loading?: boolean;
}>();

const emit = defineEmits<{
  (event: "selectLink", name: string): void;
}>();

const totalUnreadCount = computed(() => {
  return props.unreadSummary?.totalUnreadCount || 0;
});

function unreadCount(link: Link) {
  return linkFeedUnreadCount(props.unreadSummary, link.metadata.name);
}

function linkSubtitle(link: Link) {
  const feedUrls = rssFeedUrls(link);
  if (feedUrls.length > 1) {
    return `${feedUrls.length} 个订阅源`;
  }
  return link.spec?.url || feedUrls[0];
}

function itemCountText(count?: number) {
  if (count === undefined) {
    return "";
  }
  return count > 999 ? "999+" : `${count}`;
}
</script>

<template>
  <aside class=":uno: subscription-panel">
    <div class=":uno: subscription-panel__header">
      <div class=":uno: subscription-panel__title">
        <span>RSS</span>
        <strong>订阅</strong>
      </div>
      <span class=":uno: subscription-count">{{ links.length }}</span>
    </div>

    <div v-if="loading && !links.length" class=":uno: subscription-empty">加载订阅中...</div>

    <div v-else class=":uno: subscription-list">
      <button
        type="button"
        class=":uno: subscription-item"
        :class="{
          ':uno: subscription-item--active': !selectedLinkName,
        }"
        :aria-pressed="!selectedLinkName"
        @click="emit('selectLink', '')"
      >
        <span class=":uno: subscription-avatar subscription-avatar--all">
          <Rss2FillIcon class=":uno: subscription-avatar__icon" />
        </span>
        <span class=":uno: subscription-item__content">
          <span class=":uno: subscription-item__title">全部动态</span>
          <span class=":uno: subscription-item__subtitle">所有订阅源</span>
        </span>
        <span
          class=":uno: subscription-item__count"
          :class="{ ':uno: subscription-item__count--muted': !totalUnreadCount }"
        >
          {{ itemCountText(totalUnreadCount) }}
        </span>
      </button>

      <button
        v-for="link in links"
        :key="link.metadata.name"
        type="button"
        class=":uno: subscription-item"
        :class="{
          ':uno: subscription-item--active': selectedLinkName === link.metadata.name,
        }"
        :aria-pressed="selectedLinkName === link.metadata.name"
        @click="emit('selectLink', link.metadata.name)"
      >
        <img v-if="link.spec?.logo" :src="link.spec.logo" class=":uno: subscription-avatar" alt="" />
        <span v-else class=":uno: subscription-avatar subscription-avatar--fallback">
          <Rss2FillIcon class=":uno: subscription-avatar__icon" />
        </span>
        <span class=":uno: subscription-item__content">
          <span class=":uno: subscription-item__title">{{ linkTitle(link) }}</span>
          <span class=":uno: subscription-item__subtitle">{{ linkSubtitle(link) }}</span>
        </span>
        <span v-if="unreadCount(link)" class=":uno: subscription-item__count">
          {{ itemCountText(unreadCount(link)) }}
        </span>
      </button>

      <div v-if="!links.length" class=":uno: flex items-center justify-center">
        <div class=":uno: subscription-empty">暂无已订阅链接</div>
        <IconInformation
          v-tooltip="`需要在具体的链接中启用 RSS 订阅并添加链接`"
          class=":uno: size-4 text-gray-500 hover:text-gray-900"
        />
      </div>
    </div>
  </aside>
</template>

<style scoped>
.subscription-panel {
  min-width: 0;
  border: 1px solid rgb(229 231 235);
  border-radius: 8px;
  background: rgb(255 255 255);
  padding: 0.75rem;
  box-shadow: 0 1px 2px rgb(15 23 42 / 0.04);
}

.subscription-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

.subscription-panel__title {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0.125rem;
}

.subscription-panel__title span {
  color: rgb(113 113 122);
  font-size: 0.6875rem;
  font-weight: 650;
  line-height: 0.875rem;
}

.subscription-panel__title strong {
  color: rgb(24 24 27);
  font-size: 0.9375rem;
  font-weight: 650;
  line-height: 1.25rem;
}

.subscription-count {
  display: inline-flex;
  min-width: 1.75rem;
  height: 1.5rem;
  align-items: center;
  justify-content: center;
  border: 1px solid rgb(229 231 235);
  border-radius: 999px;
  background: rgb(250 250 250);
  color: rgb(82 82 91);
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1rem;
  padding: 0 0.5rem;
}

.subscription-list {
  display: flex;
  gap: 0.5rem;
  overflow-x: auto;
  padding-bottom: 0.125rem;
}

.subscription-item {
  display: flex;
  min-width: 14rem;
  flex: none;
  align-items: center;
  gap: 0.625rem;
  border: 1px solid transparent;
  border-radius: 8px;
  background: rgb(250 250 250);
  color: rgb(63 63 70);
  padding: 0.625rem;
  text-align: left;
  transition:
    background-color 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    color 0.18s ease,
    transform 0.18s ease;
}

.subscription-item:hover {
  background: rgb(244 244 245);
  color: rgb(24 24 27);
  transform: translateY(-1px);
}

.subscription-item--active {
  border-color: rgb(212 212 216);
  background: linear-gradient(180deg, rgb(255 255 255), rgb(244 244 245));
  color: rgb(24 24 27);
  box-shadow:
    inset 0 0 0 1px rgb(255 255 255 / 0.92),
    0 1px 2px rgb(15 23 42 / 0.06);
}

.subscription-item--active:hover {
  border-color: rgb(212 212 216);
  background: linear-gradient(180deg, rgb(255 255 255), rgb(244 244 245));
  color: rgb(24 24 27);
  transform: none;
}

.subscription-avatar {
  width: 1.875rem;
  height: 1.875rem;
  flex: none;
  border: 1px solid rgb(229 231 235);
  border-radius: 7px;
  background: rgb(255 255 255);
  object-fit: cover;
}

.subscription-avatar--all,
.subscription-avatar--fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: rgb(82 82 91);
}

.subscription-avatar__icon {
  width: 1rem;
  height: 1rem;
}

.subscription-item__content {
  min-width: 0;
  flex: 1;
}

.subscription-item__title,
.subscription-item__subtitle {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.subscription-item__title {
  color: inherit;
  font-size: 0.875rem;
  font-weight: 650;
  line-height: 1.25rem;
}

.subscription-item__subtitle {
  color: rgb(113 113 122);
  font-size: 0.75rem;
  line-height: 1rem;
}

.subscription-item--active .subscription-item__subtitle,
.subscription-item--active .subscription-item__count {
  color: rgb(82 82 91);
}

.subscription-item__count {
  flex: none;
  color: rgb(113 113 122);
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1rem;
}

.subscription-item__count--muted {
  color: rgb(161 161 170);
}

.subscription-empty {
  padding: 1.5rem 0.75rem;
  color: rgb(113 113 122);
  font-size: 0.8125rem;
  line-height: 1.125rem;
  text-align: center;
}

@media (min-width: 1024px) {
  .subscription-panel {
    position: sticky;
    top: 1rem;
    width: 18rem;
    max-height: calc(100vh - 2rem);
    flex: none;
    align-self: flex-start;
    overflow: hidden;
  }

  .subscription-list {
    max-height: calc(100vh - 6.5rem);
    flex-direction: column;
    overflow-x: hidden;
    overflow-y: auto;
    padding-bottom: 0;
  }

  .subscription-item {
    width: 100%;
    min-width: 0;
  }
}
</style>
