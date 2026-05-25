<script lang="ts" setup>
import type { Link, LinkFeedUnreadSummary, RssFeedStatus } from "@/api/generated";
import {
  aggregateLinkFeedStatusMeta,
  linkFeedStatusMeta,
  linkTitle,
  rssFeedUrls,
  statusSortWeight,
} from "@/composables/link-feed-status";
import { linkFeedUnreadCount } from "@/composables/use-link-feed-unread-summary";
import { VButton, VModal, VSpace } from "@halo-dev/components";
import { utils } from "@halo-dev/ui-shared";
import { computed, useTemplateRef } from "vue";

const props = defineProps<{
  links: Link[];
  selectedLinkName: string;
  unreadSummary?: LinkFeedUnreadSummary;
}>();

const emit = defineEmits<{
  (event: "close"): void;
}>();

interface DetailItem {
  label: string;
  value: string;
}

interface FeedDetail {
  itemCount: number;
  items: DetailItem[];
  status: RssFeedStatus;
  url: string;
}

const modal = useTemplateRef<InstanceType<typeof VModal> | null>("modal");

const selectedLink = computed(() => {
  return props.selectedLinkName
    ? props.links.find((link) => link.metadata.name === props.selectedLinkName)
    : undefined;
});

const isAggregate = computed(() => !selectedLink.value);
const modalTitle = computed(() => (isAggregate.value ? "全部订阅状态" : `${linkTitle(selectedLink.value!)} 状态`));
const aggregateStatus = computed(() => aggregateLinkFeedStatusMeta(props.links));
const selectedStatus = computed(() => (selectedLink.value ? linkFeedStatusMeta(selectedLink.value) : aggregateStatus.value));
const totalItemCount = computed(() => props.links.reduce((total, link) => total + (link.status?.rss?.itemCount || 0), 0));
const totalUnreadCount = computed(() => props.unreadSummary?.totalUnreadCount || 0);
const selectedUnreadCount = computed(() => linkFeedUnreadCount(props.unreadSummary, selectedLink.value?.metadata.name));

const selectedDetails = computed<DetailItem[]>(() => {
  const link = selectedLink.value;
  if (!link) {
    return [];
  }
  const rss = link.status?.rss;
  return [
    { label: "订阅源数量", value: `${rssFeedUrls(link).length}` },
    { label: "缓存文章", value: `${rss?.itemCount || 0}` },
    { label: "未读文章", value: `${selectedUnreadCount.value}` },
    { label: "最近获取", value: formatDate(rss?.lastFetchedAt) },
    { label: "最近成功", value: formatDate(rss?.lastSuccessAt) },
    { label: "最新文章", value: formatDate(rss?.latestPublishedAt) },
    { label: "失败次数", value: `${rss?.failureCount || 0}` },
    { label: "最后错误", value: rss?.lastError || "无" },
  ];
});

const aggregateDetails = computed<DetailItem[]>(() => [
  { label: "订阅数量", value: `${props.links.length}` },
  { label: "缓存文章", value: `${totalItemCount.value}` },
  { label: "未读文章", value: `${totalUnreadCount.value}` },
  { label: "状态", value: aggregateStatus.value.label },
]);

const feedDetails = computed<FeedDetail[]>(() => {
  const link = selectedLink.value;
  if (!link) {
    return [];
  }
  const statusByUrl = new Map((link.status?.rss?.feeds || []).map((feed) => [feed.url || "", feed]));
  return rssFeedUrls(link).map((url) => {
    const status = statusByUrl.get(url) || { url };
    return {
      url,
      status,
      itemCount: status.itemCount || 0,
      items: [
        { label: "最近获取", value: formatDate(status.lastFetchedAt) },
        { label: "最近成功", value: formatDate(status.lastSuccessAt) },
        { label: "最新文章", value: formatDate(status.latestPublishedAt) },
        { label: "缓存文章", value: `${status.itemCount || 0}` },
        { label: "失败次数", value: `${status.failureCount || 0}` },
        { label: "最后错误", value: status.lastError || "无" },
      ],
    };
  });
});

const subscriptionsNeedingAttention = computed(() => {
  return [...props.links]
    .filter((link) => linkFeedStatusMeta(link).state !== "success")
    .sort((left, right) => statusSortWeight(left) - statusSortWeight(right));
});

function formatDate(value?: string) {
  if (!value) {
    return "无";
  }
  return utils.date.format(value);
}
</script>

<template>
  <VModal ref="modal" :centered="false" :title="modalTitle" :mount-to-body="true" :width="760" @close="emit('close')">
    <div class=":uno: feed-status-modal">
      <section class=":uno: feed-status-modal__summary">
        <span class=":uno: feed-status-modal__state" :class="`:uno: feed-status-modal__state--${selectedStatus.tone}`">
          {{ selectedStatus.label }}
        </span>
        <p>{{ selectedStatus.description }}</p>
      </section>

      <section class=":uno: feed-status-modal__section">
        <h3>{{ isAggregate ? "汇总" : "订阅信息" }}</h3>
        <dl class=":uno: feed-status-modal__grid">
          <div v-for="item in isAggregate ? aggregateDetails : selectedDetails" :key="item.label">
            <dt>{{ item.label }}</dt>
            <dd>{{ item.value }}</dd>
          </div>
        </dl>
      </section>

      <section v-if="selectedLink" class=":uno: feed-status-modal__section">
        <h3>订阅源</h3>
        <div class=":uno: feed-status-modal__feeds">
          <article v-for="feed in feedDetails" :key="feed.url" class=":uno: feed-status-modal__feed">
            <strong>{{ feed.url }}</strong>
            <dl class=":uno: feed-status-modal__grid feed-status-modal__grid--compact">
              <div v-for="item in feed.items" :key="item.label">
                <dt>{{ item.label }}</dt>
                <dd>{{ item.value }}</dd>
              </div>
            </dl>
          </article>
        </div>
      </section>

      <section v-else class=":uno: feed-status-modal__section">
        <h3>需要关注</h3>
        <div v-if="subscriptionsNeedingAttention.length" class=":uno: feed-status-modal__feeds">
          <article
            v-for="link in subscriptionsNeedingAttention"
            :key="link.metadata.name"
            class=":uno: feed-status-modal__feed"
          >
            <strong>{{ linkTitle(link) }}</strong>
            <span>{{ linkFeedStatusMeta(link).label }}</span>
            <p v-if="link.status?.rss?.lastError">{{ link.status.rss.lastError }}</p>
          </article>
        </div>
        <p v-else class=":uno: feed-status-modal__empty">所有订阅最近均成功获取</p>
      </section>
    </div>

    <template #footer>
      <VSpace>
        <VButton @click="modal?.close()">关闭</VButton>
      </VSpace>
    </template>
  </VModal>
</template>

<style scoped>
.feed-status-modal {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.feed-status-modal__summary {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 0.75rem;
  border: 1px solid rgb(229 231 235);
  border-radius: 8px;
  background: rgb(250 250 250);
  padding: 0.75rem;
}

.feed-status-modal__summary p,
.feed-status-modal__feed p,
.feed-status-modal__empty {
  margin: 0;
  color: rgb(82 82 91);
  font-size: 0.8125rem;
  line-height: 1.25rem;
}

.feed-status-modal__state {
  display: inline-flex;
  flex: none;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1rem;
  padding: 0.25rem 0.5rem;
}

.feed-status-modal__state--success {
  background: rgb(220 252 231);
  color: rgb(21 128 61);
}

.feed-status-modal__state--warning {
  background: rgb(254 249 195);
  color: rgb(161 98 7);
}

.feed-status-modal__state--danger {
  background: rgb(254 226 226);
  color: rgb(185 28 28);
}

.feed-status-modal__state--muted {
  background: rgb(244 244 245);
  color: rgb(82 82 91);
}

.feed-status-modal__section {
  min-width: 0;
}

.feed-status-modal__section h3 {
  margin: 0 0 0.5rem;
  color: rgb(24 24 27);
  font-size: 0.875rem;
  font-weight: 650;
  line-height: 1.25rem;
}

.feed-status-modal__grid {
  display: grid;
  gap: 0.5rem;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
}

.feed-status-modal__grid div {
  min-width: 0;
  border: 1px solid rgb(229 231 235);
  border-radius: 8px;
  background: rgb(255 255 255);
  padding: 0.625rem;
}

.feed-status-modal__grid dt {
  color: rgb(113 113 122);
  font-size: 0.75rem;
  line-height: 1rem;
}

.feed-status-modal__grid dd {
  min-width: 0;
  overflow-wrap: anywhere;
  margin: 0.25rem 0 0;
  color: rgb(24 24 27);
  font-size: 0.8125rem;
  line-height: 1.25rem;
}

.feed-status-modal__feeds {
  display: flex;
  flex-direction: column;
  gap: 0.625rem;
}

.feed-status-modal__feed {
  min-width: 0;
  border: 1px solid rgb(229 231 235);
  border-radius: 8px;
  background: rgb(255 255 255);
  padding: 0.75rem;
}

.feed-status-modal__feed strong {
  display: block;
  min-width: 0;
  overflow-wrap: anywhere;
  color: rgb(24 24 27);
  font-size: 0.8125rem;
  line-height: 1.25rem;
}

.feed-status-modal__feed span {
  display: inline-flex;
  margin-top: 0.25rem;
  color: rgb(82 82 91);
  font-size: 0.75rem;
  line-height: 1rem;
}

.feed-status-modal__grid--compact {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 0.625rem;
}

@media (max-width: 767px) {
  .feed-status-modal__summary {
    align-items: flex-start;
    flex-direction: column;
  }

  .feed-status-modal__grid,
  .feed-status-modal__grid--compact {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
