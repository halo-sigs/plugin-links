<script lang="ts" setup>
import type { Link, LinkFeedItem, LinkGroup } from "@/api/generated";
import LinkFeedItemCard from "@/components/LinkFeedItemCard.vue";
import { useRssLinksFetch } from "@/composables/use-link-fetch";
import { useLinkFeedItems, type LinkFeedReadStatus } from "@/composables/use-link-feed";
import { IconArrowLeft, VButton, VLoading, VModal, VPageHeader, VSpace } from "@halo-dev/components";
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import MdiRefresh from "~icons/mdi/refresh";
import MdiRss from "~icons/mdi/rss";
import MdiStar from "~icons/mdi/star";

type LinkFeedItemsState = ReturnType<typeof useLinkFeedItems>;

const router = useRouter();
const { data: groupsWithLinks } = useRssLinksFetch();

const mainFeed = useLinkFeedItems();
const readLaterFeed = useLinkFeedItems({
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
  selectedGroupName,
  selectedReadStatus,
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

const favoriteModalVisible = ref(false);
const favoriteModal = ref<InstanceType<typeof VModal> | null>(null);

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

function handleReadStatusChange(event: Event) {
  selectReadStatus((event.target as HTMLSelectElement).value as LinkFeedReadStatus);
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
  await readLaterFeed.load();
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
</script>

<template>
  <VPageHeader title="友链动态">
    <template #icon>
      <MdiRss />
    </template>
    <template #actions>
      <VSpace>
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
    <section v-if="readLaterIsLoading || readLaterItems.length" class=":uno: mb-4">
      <div class=":uno: mb-2 flex flex-wrap items-center justify-between gap-3">
        <h2 class=":uno: text-sm text-gray-900 font-semibold">
          稍后阅读
        </h2>
        <VButton size="sm" ghost :loading="readLaterIsLoading" @click="readLaterFeed.load()">
          <template #icon>
            <MdiRefresh class=":uno: size-full" />
          </template>
          刷新
        </VButton>
      </div>
      <VLoading v-if="readLaterIsLoading && !readLaterItems.length" />
      <div v-else class=":uno: space-y-2">
        <LinkFeedItemCard
          v-for="item in readLaterItems"
          :key="item.id"
          compact
          :item="item"
          :source-name="sourceName(item.linkName)"
          :published-at-text="itemTime(item)"
          :marking-read-item-id="readLaterMarkingReadItemId"
          :marking-favorite-item-id="readLaterMarkingFavoriteItemId"
          :marking-read-later-item-id="readLaterMarkingReadLaterItemId"
          @open="handleOpenItem(readLaterFeed, $event)"
          @toggle-favorite="(target, favorite) => handleMarkItemFavorite(readLaterFeed, target, favorite)"
          @toggle-read-later="(target, readLater) => handleMarkItemReadLater(readLaterFeed, target, readLater)"
          @toggle-read="(target, read) => handleMarkItemRead(readLaterFeed, target, read)"
        />
      </div>
      <div v-if="readLaterHasNext" class=":uno: flex justify-center pt-3">
        <VButton size="sm" :loading="readLaterIsLoadingMore" @click="readLaterFeed.load({ append: true })">
          加载更多
        </VButton>
      </div>
    </section>

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
      <LinkFeedItemCard
        v-for="item in items"
        :key="item.id"
        :item="item"
        :source-name="sourceName(item.linkName)"
        :published-at-text="itemTime(item)"
        :marking-read-item-id="markingReadItemId"
        :marking-favorite-item-id="markingFavoriteItemId"
        :marking-read-later-item-id="markingReadLaterItemId"
        @open="handleOpenItem(mainFeed, $event)"
        @toggle-favorite="(target, favorite) => handleMarkItemFavorite(mainFeed, target, favorite)"
        @toggle-read-later="(target, readLater) => handleMarkItemReadLater(mainFeed, target, readLater)"
        @toggle-read="(target, read) => handleMarkItemRead(mainFeed, target, read)"
      />

      <div class=":uno: flex justify-center pt-2" v-if="hasNext">
        <VButton :loading="isLoadingMore" @click="load({ append: true })">加载更多</VButton>
      </div>
    </div>
  </div>

  <VModal
    ref="favoriteModal"
    v-model:visible="favoriteModalVisible"
    :centered="false"
    title="收藏文章"
    :mount-to-body="true"
    :width="860"
  >
    <VLoading v-if="favoriteIsLoading && !favoriteItems.length" />
    <div
      v-else-if="!favoriteItems.length"
      class=":uno: border border-gray-100 rounded-lg bg-white px-4 py-10 text-center text-sm text-gray-500"
    >
      暂无收藏文章
    </div>
    <div v-else class=":uno: max-h-[65vh] overflow-auto pr-1 space-y-3">
      <LinkFeedItemCard
        v-for="item in favoriteItems"
        :key="item.id"
        :item="item"
        :source-name="sourceName(item.linkName)"
        :published-at-text="itemTime(item)"
        :marking-read-item-id="favoriteMarkingReadItemId"
        :marking-favorite-item-id="favoriteMarkingFavoriteItemId"
        :marking-read-later-item-id="favoriteMarkingReadLaterItemId"
        @open="handleOpenItem(favoriteFeed, $event)"
        @toggle-favorite="(target, favorite) => handleMarkItemFavorite(favoriteFeed, target, favorite)"
        @toggle-read-later="(target, readLater) => handleMarkItemReadLater(favoriteFeed, target, readLater)"
        @toggle-read="(target, read) => handleMarkItemRead(favoriteFeed, target, read)"
      />
      <div v-if="favoriteHasNext" class=":uno: flex justify-center pt-2">
        <VButton :loading="favoriteIsLoadingMore" @click="favoriteFeed.load({ append: true })">加载更多</VButton>
      </div>
    </div>

    <template #footer>
      <VSpace>
        <VButton type="secondary" :loading="favoriteIsLoading" @click="favoriteFeed.load()">刷新</VButton>
        <VButton @click="favoriteModal?.close()">关闭</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
