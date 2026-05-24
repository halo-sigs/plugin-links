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
    <div class=":uno: min-w-0 flex flex-col gap-4 lg:flex-row lg:items-start">
      <LinkFeedSubscriptionSidebar
        :links="allLinks"
        :selected-link-name="selectedLinkName"
        :loading="isLoadingLinks"
        @select-link="selectLink"
      />

      <div class=":uno: min-w-0 flex-1">
        <div class=":uno: mb-4 flex flex-wrap items-center justify-between gap-3">
          <LinkFeedReadStatusTabs :selected-status="selectedReadStatus" @select="selectReadStatus" />
          <div class=":uno: flex flex-wrap items-center justify-end gap-3">
            <span v-if="refreshProgressText" class=":uno: text-xs text-gray-500" role="status">
              {{ refreshProgressText }}
            </span>
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
