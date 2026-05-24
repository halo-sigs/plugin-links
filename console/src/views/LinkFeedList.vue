<script lang="ts" setup>
import type { Link } from "@/api/generated";
import LinkFeedItemList from "@/components/LinkFeedItemList.vue";
import LinkFeedReadStatusTabs from "@/components/LinkFeedReadStatusTabs.vue";
import LinkFeedSubscriptionSidebar from "@/components/LinkFeedSubscriptionSidebar.vue";
import { useLinkFeedItems } from "@/composables/use-link-feed";
import { useLinkFeedRefresh, type LinkFeedRefreshSummary } from "@/composables/use-link-feed-refresh";
import { useRssLinksFetch } from "@/composables/use-link-fetch";
import { IconArrowLeft, Toast, VButton, VPageHeader, VSpace } from "@halo-dev/components";
import { computed, defineAsyncComponent, shallowRef } from "vue";
import { useRouter } from "vue-router";
import MdiClockCheckOutline from "~icons/mdi/clock-check-outline";
import MdiRefresh from "~icons/mdi/refresh";
import MdiRss from "~icons/mdi/rss";
import MdiStar from "~icons/mdi/star";

const LinkFeedItemsModal = defineAsyncComponent(
  () => import(/* webpackChunkName: "link-feed-items-modal" */ "@/components/LinkFeedItemsModal.vue"),
);

const router = useRouter();
const { data: groupsWithLinks, isLoading: isLoadingLinks } = useRssLinksFetch();

const favoriteModalVisible = shallowRef(false);
const readLaterModalVisible = shallowRef(false);
const mainFeed = useLinkFeedItems();

const readLaterFeed = useLinkFeedItems({
  enabled: readLaterModalVisible,
  fixedFilter: {
    readLater: true,
  },
});

const favoriteFeed = useLinkFeedItems({
  enabled: favoriteModalVisible,
  fixedFilter: {
    favorite: true,
  },
});

const { selectedLinkName, selectedReadStatus, isFetching, reload, selectLink, selectReadStatus } = mainFeed;

const {
  isRefreshing: isRefreshingCurrentSubscription,
  totalCount: currentRefreshTotalCount,
  completedCount: currentRefreshCompletedCount,
  refreshLinks: refreshCurrentSubscriptions,
} = useLinkFeedRefresh();
const {
  isRefreshing: isRefreshingAllSubscriptions,
  totalCount: allRefreshTotalCount,
  completedCount: allRefreshCompletedCount,
  refreshLinks: refreshAllSubscriptions,
} = useLinkFeedRefresh();

const allLinks = computed(() => {
  return groupsWithLinks.value?.flatMap((group) => group.links) || [];
});

const linkByName = computed(() => {
  return new Map(allLinks.value.map((link) => [link.metadata.name, link]));
});

const selectedLink = computed(() => {
  return selectedLinkName.value ? linkByName.value.get(selectedLinkName.value) : undefined;
});

const totalFeedItemCount = computed(() => {
  return allLinks.value.reduce((total, link) => total + (link.status?.rss?.itemCount || 0), 0);
});

const selectedSourceTitle = computed(() => {
  if (!selectedLink.value) {
    return "全部动态";
  }
  return selectedLink.value.spec?.displayName || selectedLink.value.metadata.name;
});

const selectedSourceMeta = computed(() => {
  if (!selectedLink.value) {
    return `${allLinks.value.length} 个订阅 / ${totalFeedItemCount.value} 篇缓存`;
  }

  const feedCount = selectedLink.value.spec?.rss?.feedUrls?.filter((feedUrl) => !!feedUrl?.trim()).length || 0;
  const itemCount = selectedLink.value.status?.rss?.itemCount || 0;
  return `${feedCount} 个订阅源 / ${itemCount} 篇缓存`;
});

const isRemoteRefreshing = computed(() => {
  return isRefreshingCurrentSubscription.value || isRefreshingAllSubscriptions.value;
});

const currentRefreshButtonText = computed(() => {
  return refreshButtonText(
    "刷新当前",
    isRefreshingCurrentSubscription.value,
    currentRefreshCompletedCount.value,
    currentRefreshTotalCount.value,
  );
});

const allRefreshButtonText = computed(() => {
  return refreshButtonText(
    "刷新全部",
    isRefreshingAllSubscriptions.value,
    allRefreshCompletedCount.value,
    allRefreshTotalCount.value,
  );
});

const refreshProgressText = computed(() => {
  if (isRefreshingCurrentSubscription.value) {
    return refreshProgressTextByCount(
      "正在刷新当前订阅",
      currentRefreshCompletedCount.value,
      currentRefreshTotalCount.value,
    );
  }
  if (isRefreshingAllSubscriptions.value) {
    return refreshProgressTextByCount("正在刷新全部订阅", allRefreshCompletedCount.value, allRefreshTotalCount.value);
  }
  return "";
});

function sourceLink(linkName?: string): Link | undefined {
  if (!linkName) {
    return undefined;
  }
  return linkByName.value.get(linkName);
}

function sourceName(linkName?: string) {
  if (!linkName) {
    return "未知来源";
  }
  const link = sourceLink(linkName);
  if (link) {
    return link.spec?.displayName || link.metadata.name;
  }
  return isLoadingLinks.value ? "加载来源中" : "已删除链接";
}

function openReadLaterModal() {
  readLaterModalVisible.value = true;
}

function openFavoriteModal() {
  favoriteModalVisible.value = true;
}

async function handleRefreshCurrentSubscription() {
  if (!selectedLink.value || isRemoteRefreshing.value) {
    return;
  }
  const summary = await refreshCurrentSubscriptions(selectedLink.value);
  await reload();
  showRefreshSummary(summary, "当前订阅");
}

async function handleRefreshAllSubscriptions() {
  if (!allLinks.value.length || isRemoteRefreshing.value) {
    return;
  }
  const summary = await refreshAllSubscriptions(allLinks.value);
  await reload();
  showRefreshSummary(summary, "全部订阅");
}

function showRefreshSummary(summary: LinkFeedRefreshSummary | undefined, label: string) {
  if (!summary || !summary.totalCount) {
    return;
  }

  if (!summary.failureCount && !summary.partialFailureCount) {
    Toast.success(`${label}刷新完成：成功刷新 ${summary.successCount} 个订阅`);
    return;
  }

  const resultText = refreshSummaryText(summary);
  if (summary.failureCount === summary.totalCount) {
    Toast.error(`${label}刷新失败：${resultText}`);
    return;
  }

  Toast.warning(`${label}刷新完成：${resultText}`);
}

function refreshButtonText(label: string, refreshing: boolean, completedCount: number, totalCount: number) {
  if (!refreshing || !totalCount) {
    return label;
  }
  return `${label} ${completedCount}/${totalCount}`;
}

function refreshProgressTextByCount(label: string, completedCount: number, totalCount: number) {
  if (!totalCount) {
    return label;
  }
  return `${label}：${completedCount}/${totalCount}`;
}

function refreshSummaryText(summary: LinkFeedRefreshSummary) {
  return [
    summary.successCount ? `成功 ${summary.successCount} 个` : "",
    summary.partialFailureCount ? `部分失败 ${summary.partialFailureCount} 个` : "",
    summary.failureCount ? `失败 ${summary.failureCount} 个` : "",
  ]
    .filter(Boolean)
    .join("，");
}
</script>

<template>
  <VPageHeader title="友链动态">
    <template #icon>
      <MdiRss />
    </template>
    <template #actions>
      <VSpace>
        <VButton size="sm" @click="openReadLaterModal">
          <template #icon>
            <MdiClockCheckOutline class=":uno: size-full text-blue-600" />
          </template>
          稍后阅读
        </VButton>
        <VButton size="sm" @click="openFavoriteModal">
          <template #icon>
            <MdiStar class=":uno: size-full text-yellow-500" />
          </template>
          收藏
        </VButton>
        <VButton size="sm" @click="router.push({ name: 'Links' })">
          <template #icon>
            <IconArrowLeft class=":uno: size-full" />
          </template>
          返回链接
        </VButton>
      </VSpace>
    </template>
  </VPageHeader>

  <div class=":uno: p-4">
    <div class=":uno: feed-workspace">
      <LinkFeedSubscriptionSidebar
        :links="allLinks"
        :selected-link-name="selectedLinkName"
        :loading="isLoadingLinks"
        @select-link="selectLink"
      />

      <div class=":uno: feed-main">
        <div class=":uno: feed-brief">
          <div class=":uno: feed-brief__copy">
            <span class=":uno: feed-brief__eyebrow">RSS 动态</span>
            <strong class=":uno: feed-brief__title">{{ selectedSourceTitle }}</strong>
            <span class=":uno: feed-brief__meta">{{ selectedSourceMeta }}</span>
          </div>
          <span v-if="refreshProgressText" class=":uno: feed-refresh-status" role="status">
            {{ refreshProgressText }}
          </span>
        </div>

        <div class=":uno: feed-toolbar">
          <LinkFeedReadStatusTabs :selected-status="selectedReadStatus" @select="selectReadStatus" />
          <div class=":uno: feed-toolbar__actions">
            <VSpace>
              <VButton
                size="sm"
                :disabled="!selectedLink || isRemoteRefreshing"
                :loading="isRefreshingCurrentSubscription"
                @click="handleRefreshCurrentSubscription"
              >
                <template #icon>
                  <MdiRefresh class=":uno: size-full" />
                </template>
                {{ currentRefreshButtonText }}
              </VButton>
              <VButton
                size="sm"
                :disabled="!allLinks.length || isRemoteRefreshing"
                :loading="isRefreshingAllSubscriptions"
                @click="handleRefreshAllSubscriptions"
              >
                <template #icon>
                  <MdiRefresh class=":uno: size-full" />
                </template>
                {{ allRefreshButtonText }}
              </VButton>
              <VButton size="sm" ghost :loading="isFetching" @click="reload()">
                <template #icon>
                  <MdiRefresh class=":uno: size-full" />
                </template>
                重新加载
              </VButton>
            </VSpace>
          </div>
        </div>

        <LinkFeedItemList :feed="mainFeed" :source-name="sourceName" empty-text="暂无友链动态" />
      </div>
    </div>
  </div>

  <LinkFeedItemsModal
    v-if="readLaterModalVisible"
    title="稍后阅读"
    :feed="readLaterFeed"
    :source-name="sourceName"
    empty-text="暂无稍后阅读文章"
    @close="readLaterModalVisible = false"
  />

  <LinkFeedItemsModal
    v-if="favoriteModalVisible"
    title="收藏文章"
    :feed="favoriteFeed"
    :source-name="sourceName"
    empty-text="暂无收藏文章"
    @close="favoriteModalVisible = false"
  />
</template>

<style scoped>
.feed-workspace {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1rem;
}

.feed-main {
  min-width: 0;
  flex: 1;
}

.feed-brief {
  display: flex;
  min-width: 0;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
  border: 1px solid rgb(229 231 235);
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgb(255 255 255 / 0.92), rgb(250 250 250 / 0.88)),
    repeating-linear-gradient(135deg, rgb(24 24 27 / 0.035) 0 1px, transparent 1px 12px);
  padding: 1rem;
  box-shadow: 0 1px 2px rgb(15 23 42 / 0.04);
}

.feed-brief__copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.feed-brief__eyebrow {
  color: rgb(113 113 122);
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1rem;
}

.feed-brief__title {
  min-width: 0;
  overflow: hidden;
  color: rgb(24 24 27);
  font-size: 1.125rem;
  font-weight: 650;
  line-height: 1.5rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feed-brief__meta,
.feed-refresh-status {
  color: rgb(113 113 122);
  font-size: 0.8125rem;
  line-height: 1.125rem;
}

.feed-refresh-status {
  flex: none;
  border: 1px solid rgb(229 231 235);
  border-radius: 999px;
  background: rgb(255 255 255 / 0.76);
  padding: 0.375rem 0.625rem;
}

.feed-toolbar {
  position: sticky;
  top: 0.75rem;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin: 0.75rem 0;
  border: 1px solid rgb(229 231 235);
  border-radius: 8px;
  background: rgb(255 255 255 / 0.9);
  padding: 0.5rem;
  box-shadow: 0 1px 2px rgb(15 23 42 / 0.04);
  backdrop-filter: blur(14px);
}

.feed-toolbar__actions {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.5rem;
}

@media (min-width: 1024px) {
  .feed-workspace {
    flex-direction: row;
    align-items: flex-start;
  }
}

@media (max-width: 767px) {
  .feed-brief,
  .feed-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .feed-refresh-status {
    align-self: flex-start;
  }

  .feed-toolbar__actions {
    justify-content: flex-start;
  }
}
</style>
