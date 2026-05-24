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

const { selectedLinkName, selectedReadStatus, isLoading, reload, selectLink, selectReadStatus } = mainFeed;

const { isRefreshing: isRefreshingCurrentSubscription, refreshLinks: refreshCurrentSubscriptions } =
  useLinkFeedRefresh();
const { isRefreshing: isRefreshingAllSubscriptions, refreshLinks: refreshAllSubscriptions } = useLinkFeedRefresh();

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

function sourceLink(linkName?: string): Link | undefined {
  if (!linkName) {
    return undefined;
  }
  return linkByName.value.get(linkName);
}

function sourceName(linkName?: string) {
  return sourceLink(linkName)?.spec?.displayName || linkName || "未知链接";
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

  if (summary.failureCount) {
    return;
  }

  if (!summary.partialFailureCount) {
    Toast.success(`${label}刷新完成：成功刷新 ${summary.successCount} 个订阅`);
    return;
  }

  Toast.warning(`${label}刷新完成：成功 ${summary.successCount} 个，${summary.partialFailureCount} 个存在失败`);
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
              刷新当前
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
              刷新全部
            </VButton>
            <VButton size="sm" ghost :loading="isLoading" @click="reload()">
              <template #icon>
                <MdiRefresh class=":uno: size-full" />
              </template>
              重新加载
            </VButton>
          </VSpace>
        </div>

        <LinkFeedItemList
          :feed="mainFeed"
          :source-name="sourceName"
          empty-text="暂无友链动态"
        />
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
