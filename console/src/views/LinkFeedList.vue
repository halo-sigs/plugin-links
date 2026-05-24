<script lang="ts" setup>
import type { Link, LinkFeedItem } from "@/api/generated";
import LinkFeedItemList from "@/components/LinkFeedItemList.vue";
import LinkFeedReadStatusTabs from "@/components/LinkFeedReadStatusTabs.vue";
import LinkFeedSubscriptionSidebar from "@/components/LinkFeedSubscriptionSidebar.vue";
import { useLinkFeedItems } from "@/composables/use-link-feed";
import { useLinkFeedRefresh, type LinkFeedRefreshSummary } from "@/composables/use-link-feed-refresh";
import { useRssLinksFetch } from "@/composables/use-link-fetch";
import { IconArrowLeft, Toast, VButton, VModal, VPageHeader, VSpace } from "@halo-dev/components";
import { computed, shallowRef, useTemplateRef } from "vue";
import { useRouter } from "vue-router";
import MdiClockCheckOutline from "~icons/mdi/clock-check-outline";
import MdiRefresh from "~icons/mdi/refresh";
import MdiRss from "~icons/mdi/rss";
import MdiStar from "~icons/mdi/star";

type LinkFeedItemsState = ReturnType<typeof useLinkFeedItems>;

const router = useRouter();
const { data: groupsWithLinks, isLoading: isLoadingLinks } = useRssLinksFetch();

const mainFeed = useLinkFeedItems();
const readLaterFeed = useLinkFeedItems({
  autoLoad: false,
  fixedFilter: {
    readLater: true,
  },
});
const favoriteFeed = useLinkFeedItems({
  autoLoad: false,
  fixedFilter: {
    favorite: true,
  },
});

const {
  items,
  selectedLinkName,
  selectedReadStatus,
  hasNext,
  isLoading,
  isLoadingMore,
  markingReadItemId,
  markingFavoriteItemId,
  markingReadLaterItemId,
  load,
  selectLink,
  selectReadStatus,
} = mainFeed;

const {
  items: readLaterItems,
  hasNext: readLaterHasNext,
  isLoading: readLaterIsLoading,
  isLoadingMore: readLaterIsLoadingMore,
  markingReadItemId: readLaterMarkingReadItemId,
  markingFavoriteItemId: readLaterMarkingFavoriteItemId,
  markingReadLaterItemId: readLaterMarkingReadLaterItemId,
} = readLaterFeed;

const {
  items: favoriteItems,
  hasNext: favoriteHasNext,
  isLoading: favoriteIsLoading,
  isLoadingMore: favoriteIsLoadingMore,
  markingReadItemId: favoriteMarkingReadItemId,
  markingFavoriteItemId: favoriteMarkingFavoriteItemId,
  markingReadLaterItemId: favoriteMarkingReadLaterItemId,
} = favoriteFeed;

const favoriteModalVisible = shallowRef(false);
const favoriteModal = useTemplateRef<InstanceType<typeof VModal>>("favoriteModal");
const readLaterModalVisible = shallowRef(false);
const readLaterModal = useTemplateRef<InstanceType<typeof VModal>>("readLaterModal");
const {
  isRefreshing: isRefreshingCurrentSubscription,
  refreshLinks: refreshCurrentSubscriptions,
} = useLinkFeedRefresh();
const {
  isRefreshing: isRefreshingAllSubscriptions,
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

function sourceLink(linkName?: string): Link | undefined {
  if (!linkName) {
    return undefined;
  }
  return linkByName.value.get(linkName);
}

function sourceName(linkName?: string) {
  return sourceLink(linkName)?.spec?.displayName || linkName || "未知链接";
}

async function openReadLaterModal() {
  readLaterModalVisible.value = true;
  await readLaterFeed.load();
}

async function openFavoriteModal() {
  favoriteModalVisible.value = true;
  await favoriteFeed.load();
}

async function handleOpenItem(feed: LinkFeedItemsState, item: LinkFeedItem) {
  const updated = await feed.openItem(item);
  if (!updated) {
    return;
  }
  syncItemState(item);
}

async function handleMarkItemRead(feed: LinkFeedItemsState, item: LinkFeedItem, read: boolean) {
  const updated = await feed.markItemRead(item, read);
  if (!updated) {
    return;
  }
  syncItemState(item);
}

async function handleMarkItemFavorite(feed: LinkFeedItemsState, item: LinkFeedItem, favorite: boolean) {
  const updated = await feed.markItemFavorite(item, favorite);
  if (!updated) {
    return;
  }
  syncItemState(item);
  if (favoriteModalVisible.value) {
    await favoriteFeed.load();
  }
}

async function handleMarkItemReadLater(feed: LinkFeedItemsState, item: LinkFeedItem, readLater: boolean) {
  const updated = await feed.markItemReadLater(item, readLater);
  if (!updated) {
    return;
  }
  syncItemState(item);
  if (readLaterModalVisible.value) {
    await readLaterFeed.load();
  }
}

function syncItemState(item: LinkFeedItem) {
  mainFeed.patchItemState(item);
  readLaterFeed.patchItemState(item);
  favoriteFeed.patchItemState(item);
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

function itemTime(item: LinkFeedItem) {
  return formatTime(item.publishedAt || item.updatedAt || item.fetchedAt);
}

async function handleRefreshCurrentSubscription() {
  if (!selectedLink.value || isRemoteRefreshing.value) {
    return;
  }
  const summary = await refreshCurrentSubscriptions(selectedLink.value);
  await load();
  showRefreshSummary(summary, "当前订阅");
}

async function handleRefreshAllSubscriptions() {
  if (!allLinks.value.length || isRemoteRefreshing.value) {
    return;
  }
  const summary = await refreshAllSubscriptions(allLinks.value);
  await load();
  showRefreshSummary(summary, "全部订阅");
}

function showRefreshSummary(summary: LinkFeedRefreshSummary | undefined, label: string) {
  if (!summary || !summary.totalCount) {
    return;
  }

  const failedLikeCount = summary.failureCount + summary.partialFailureCount;
  if (!failedLikeCount) {
    Toast.success(`${label}刷新完成：成功刷新 ${summary.successCount} 个订阅`);
    return;
  }

  if (summary.failureCount === summary.totalCount) {
    Toast.error(`${label}刷新失败：${summary.failureCount} 个订阅未刷新成功`);
    return;
  }

  Toast.warning(`${label}刷新完成：成功 ${summary.successCount} 个，${failedLikeCount} 个存在失败`);
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
            <VButton size="sm" ghost :loading="isLoading" @click="load()">
              <template #icon>
                <MdiRefresh class=":uno: size-full" />
              </template>
              重新加载
            </VButton>
          </VSpace>
        </div>

        <LinkFeedItemList
          :items="items"
          :source-name="sourceName"
          :published-at-text="itemTime"
          empty-text="暂无友链动态"
          :has-next="hasNext"
          :is-loading="isLoading"
          :is-loading-more="isLoadingMore"
          :marking-read-item-id="markingReadItemId"
          :marking-favorite-item-id="markingFavoriteItemId"
          :marking-read-later-item-id="markingReadLaterItemId"
          @open="handleOpenItem(mainFeed, $event)"
          @toggle-favorite="(target, favorite) => handleMarkItemFavorite(mainFeed, target, favorite)"
          @toggle-read-later="(target, readLater) => handleMarkItemReadLater(mainFeed, target, readLater)"
          @toggle-read="(target, read) => handleMarkItemRead(mainFeed, target, read)"
          @load-more="mainFeed.load({ append: true })"
        />
      </div>
    </div>
  </div>

  <VModal
    ref="readLaterModal"
    v-model:visible="readLaterModalVisible"
    :centered="false"
    title="稍后阅读"
    :mount-to-body="true"
    :width="860"
  >
    <LinkFeedItemList
      :items="readLaterItems"
      :source-name="sourceName"
      :published-at-text="itemTime"
      empty-text="暂无稍后阅读文章"
      scrollable
      :has-next="readLaterHasNext"
      :is-loading="readLaterIsLoading"
      :is-loading-more="readLaterIsLoadingMore"
      :marking-read-item-id="readLaterMarkingReadItemId"
      :marking-favorite-item-id="readLaterMarkingFavoriteItemId"
      :marking-read-later-item-id="readLaterMarkingReadLaterItemId"
      @open="handleOpenItem(readLaterFeed, $event)"
      @toggle-favorite="(target, favorite) => handleMarkItemFavorite(readLaterFeed, target, favorite)"
      @toggle-read-later="(target, readLater) => handleMarkItemReadLater(readLaterFeed, target, readLater)"
      @toggle-read="(target, read) => handleMarkItemRead(readLaterFeed, target, read)"
      @load-more="readLaterFeed.load({ append: true })"
    />

    <template #footer>
      <VSpace>
        <VButton type="secondary" :loading="readLaterIsLoading" @click="readLaterFeed.load()">重新加载</VButton>
        <VButton @click="readLaterModal?.close()">关闭</VButton>
      </VSpace>
    </template>
  </VModal>

  <VModal
    ref="favoriteModal"
    v-model:visible="favoriteModalVisible"
    :centered="false"
    title="收藏文章"
    :mount-to-body="true"
    :width="860"
  >
    <LinkFeedItemList
      :items="favoriteItems"
      :source-name="sourceName"
      :published-at-text="itemTime"
      empty-text="暂无收藏文章"
      scrollable
      :has-next="favoriteHasNext"
      :is-loading="favoriteIsLoading"
      :is-loading-more="favoriteIsLoadingMore"
      :marking-read-item-id="favoriteMarkingReadItemId"
      :marking-favorite-item-id="favoriteMarkingFavoriteItemId"
      :marking-read-later-item-id="favoriteMarkingReadLaterItemId"
      @open="handleOpenItem(favoriteFeed, $event)"
      @toggle-favorite="(target, favorite) => handleMarkItemFavorite(favoriteFeed, target, favorite)"
      @toggle-read-later="(target, readLater) => handleMarkItemReadLater(favoriteFeed, target, readLater)"
      @toggle-read="(target, read) => handleMarkItemRead(favoriteFeed, target, read)"
      @load-more="favoriteFeed.load({ append: true })"
    />

    <template #footer>
      <VSpace>
        <VButton type="secondary" :loading="favoriteIsLoading" @click="favoriteFeed.load()">重新加载</VButton>
        <VButton @click="favoriteModal?.close()">关闭</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
