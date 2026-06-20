<script lang="ts" setup>
import type { Link, LinkFeedUnreadSummary, RssFeedStatus } from "@/api/generated";
import {
  aggregateLinkFeedStatusMeta,
  linkFeedStatusMeta,
  linkTitle,
  rssFeedUrls,
  statusSortWeight,
  type LinkFeedStatusTone,
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
  label: string;
  status: RssFeedStatus;
  tone: LinkFeedStatusTone;
  url: string;
}

interface AttentionDetail {
  description: string;
  label: string;
  name: string;
  title: string;
  tone: LinkFeedStatusTone;
}

const modal = useTemplateRef<InstanceType<typeof VModal> | null>("modal");

const selectedLink = computed(() => {
  return props.selectedLinkName ? props.links.find((link) => link.metadata.name === props.selectedLinkName) : undefined;
});

const isAggregate = computed(() => !selectedLink.value);
const modalTitle = computed(() => (isAggregate.value ? "全部订阅状态" : `${linkTitle(selectedLink.value!)} 状态`));
const aggregateStatus = computed(() => aggregateLinkFeedStatusMeta(props.links));
const selectedStatus = computed(() =>
  selectedLink.value ? linkFeedStatusMeta(selectedLink.value) : aggregateStatus.value,
);
const totalItemCount = computed(() =>
  props.links.reduce((total, link) => total + (link.status?.rss?.itemCount || 0), 0),
);
const totalUnreadCount = computed(() => props.unreadSummary?.totalUnreadCount || 0);
const selectedUnreadCount = computed(() => linkFeedUnreadCount(props.unreadSummary, selectedLink.value?.metadata.name));

const selectedOverviewDetails = computed<DetailItem[]>(() => {
  const link = selectedLink.value;
  if (!link) {
    return [];
  }
  const rss = link.status?.rss;
  return [
    { label: "订阅源数量", value: `${rssFeedUrls(link).length}` },
    { label: "缓存文章", value: `${rss?.itemCount || 0}` },
    { label: "未读文章", value: `${selectedUnreadCount.value}` },
    { label: "失败次数", value: `${rss?.failureCount || 0}` },
  ];
});

const selectedActivityDetails = computed<DetailItem[]>(() => {
  const link = selectedLink.value;
  if (!link) {
    return [];
  }
  const rss = link.status?.rss;
  return [
    { label: "最近获取", value: formatDate(rss?.lastFetchedAt) },
    { label: "最近成功", value: formatDate(rss?.lastSuccessAt) },
    { label: "最新文章", value: formatDate(rss?.latestPublishedAt) },
    { label: "最后错误", value: rss?.lastError || "无" },
  ];
});

const aggregateDetails = computed<DetailItem[]>(() => [
  { label: "订阅数量", value: `${props.links.length}` },
  { label: "缓存文章", value: `${totalItemCount.value}` },
  { label: "未读文章", value: `${totalUnreadCount.value}` },
  { label: "需关注", value: `${props.links.filter((link) => linkFeedStatusMeta(link).state !== "success").length}` },
]);

const feedDetails = computed<FeedDetail[]>(() => {
  const link = selectedLink.value;
  if (!link) {
    return [];
  }
  const statusByUrl = new Map((link.status?.rss?.feeds || []).map((feed) => [feed.url || "", feed]));
  return rssFeedUrls(link).map((url) => {
    const status = statusByUrl.get(url) || ({ url } as RssFeedStatus);
    return {
      url,
      status,
      itemCount: status.itemCount || 0,
      tone: feedStatusTone(status),
      label: feedStatusLabel(status),
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

const subscriptionsNeedingAttention = computed<AttentionDetail[]>(() => {
  return [...props.links]
    .filter((link) => linkFeedStatusMeta(link).state !== "success")
    .sort((left, right) => statusSortWeight(left) - statusSortWeight(right))
    .map((link) => {
      const meta = linkFeedStatusMeta(link);
      return {
        name: link.metadata.name,
        title: linkTitle(link),
        label: meta.label,
        description: link.status?.rss?.lastError || meta.description,
        tone: meta.tone,
      };
    });
});

function formatDate(value?: string) {
  if (!value) {
    return "无";
  }
  return utils.date.format(value);
}

function feedStatusTone(status: RssFeedStatus): LinkFeedStatusTone {
  if (status.lastError) {
    return "danger";
  }
  if (status.lastSuccessAt) {
    return "success";
  }
  return "muted";
}

function feedStatusLabel(status: RssFeedStatus) {
  if (status.lastError) {
    return "获取失败";
  }
  if (status.lastSuccessAt) {
    return "正常";
  }
  return "等待获取";
}
</script>

<template>
  <VModal
    ref="modal"
    layer-closable
    :centered="false"
    :title="modalTitle"
    :mount-to-body="true"
    :width="760"
    @close="emit('close')"
  >
    <div class=":uno: feed-status-modal">
      <section class=":uno: feed-status-modal__summary">
        <span
          class=":uno: feed-status-modal__dot"
          :class="`:uno: feed-status-modal__dot--${selectedStatus.tone}`"
        ></span>
        <div class=":uno: feed-status-modal__summary-copy">
          <span class=":uno: feed-status-modal__state-label">{{ selectedStatus.label }}</span>
          <p class=":uno: feed-status-modal__description">{{ selectedStatus.description }}</p>
        </div>
      </section>

      <section class=":uno: feed-status-modal__section">
        <div class=":uno: feed-status-modal__section-heading">
          <h3 class=":uno: feed-status-modal__section-title">{{ isAggregate ? "汇总" : "订阅信息" }}</h3>
        </div>
        <dl class=":uno: feed-status-modal__metrics">
          <div v-for="item in isAggregate ? aggregateDetails : selectedOverviewDetails" :key="item.label">
            <dt class=":uno: feed-status-modal__metric-label">{{ item.label }}</dt>
            <dd class=":uno: feed-status-modal__metric-value">{{ item.value }}</dd>
          </div>
        </dl>
      </section>

      <section v-if="selectedLink" class=":uno: feed-status-modal__section">
        <div class=":uno: feed-status-modal__section-heading">
          <h3 class=":uno: feed-status-modal__section-title">运行状态</h3>
        </div>
        <dl class=":uno: feed-status-modal__detail-list">
          <div v-for="item in selectedActivityDetails" :key="item.label">
            <dt>{{ item.label }}</dt>
            <dd>{{ item.value }}</dd>
          </div>
        </dl>
      </section>

      <section v-if="selectedLink" class=":uno: feed-status-modal__section">
        <div class=":uno: feed-status-modal__section-heading">
          <h3 class=":uno: feed-status-modal__section-title">
            订阅源
            <span class=":uno: feed-status-modal__section-note">{{ feedDetails.length }} 个源</span>
          </h3>
        </div>
        <div class=":uno: feed-status-modal__feeds">
          <article v-for="feed in feedDetails" :key="feed.url" class=":uno: feed-status-modal__feed">
            <div class=":uno: feed-status-modal__feed-header">
              <span class=":uno: feed-status-modal__dot" :class="`:uno: feed-status-modal__dot--${feed.tone}`"></span>
              <strong class=":uno: feed-status-modal__feed-url">{{ feed.url }}</strong>
              <span class=":uno: feed-status-modal__feed-label">{{ feed.label }}</span>
            </div>
            <dl class=":uno: feed-status-modal__feed-grid">
              <div v-for="item in feed.items" :key="item.label">
                <dt>{{ item.label }}</dt>
                <dd>{{ item.value }}</dd>
              </div>
            </dl>
          </article>
        </div>
      </section>

      <section v-else class=":uno: feed-status-modal__section">
        <div class=":uno: feed-status-modal__section-heading">
          <h3 class=":uno: feed-status-modal__section-title">需要关注</h3>
        </div>
        <div v-if="subscriptionsNeedingAttention.length" class=":uno: feed-status-modal__feeds">
          <article
            v-for="link in subscriptionsNeedingAttention"
            :key="link.name"
            class=":uno: feed-status-modal__attention-row"
          >
            <span class=":uno: feed-status-modal__dot" :class="`:uno: feed-status-modal__dot--${link.tone}`"></span>
            <div class=":uno: feed-status-modal__attention-copy">
              <strong class=":uno: feed-status-modal__attention-title">{{ link.title }}</strong>
              <p class=":uno: feed-status-modal__description">{{ link.description }}</p>
            </div>
            <span class=":uno: feed-status-modal__attention-label">{{ link.label }}</span>
          </article>
        </div>
        <p v-else class=":uno: feed-status-modal__empty">
          <span class=":uno: feed-status-modal__dot feed-status-modal__dot--success" aria-hidden="true"></span>
          所有订阅最近均成功获取
        </p>
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
  --feed-status-line: rgb(229 231 235);
  --feed-status-line-soft: rgb(244 244 245);
  --feed-status-muted: rgb(113 113 122);
  --feed-status-text: rgb(24 24 27);
  --feed-status-surface: rgb(250 250 250);

  display: flex;
  flex-direction: column;
  gap: 1.125rem;
  color: var(--feed-status-text);
}

.feed-status-modal__summary {
  display: grid;
  min-width: 0;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 0.625rem;
  border: 1px solid var(--feed-status-line);
  border-radius: 10px;
  background: var(--feed-status-surface);
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 0.9),
    0 1px 2px rgb(0 0 0 / 0.03);
  padding: 0.875rem 1rem;
}

.feed-status-modal__summary-copy {
  min-width: 0;
}

.feed-status-modal__state-label {
  display: block;
  color: var(--feed-status-text);
  font-size: 0.8125rem;
  font-weight: 650;
  line-height: 1.25rem;
}

.feed-status-modal__description,
.feed-status-modal__empty {
  margin: 0;
  color: rgb(82 82 91);
  font-size: 0.8125rem;
  line-height: 1.25rem;
}

.feed-status-modal__dot {
  display: inline-flex;
  width: 0.5rem;
  height: 0.5rem;
  flex: none;
  border-radius: 999px;
  background: currentColor;
  box-shadow: 0 0 0 0.25rem currentColor;
  opacity: 0.9;
}

.feed-status-modal__dot--success {
  color: rgb(34 197 94 / 0.16);
  background: rgb(22 163 74);
}

.feed-status-modal__dot--warning {
  color: rgb(234 179 8 / 0.18);
  background: rgb(202 138 4);
}

.feed-status-modal__dot--danger {
  color: rgb(239 68 68 / 0.16);
  background: rgb(220 38 38);
}

.feed-status-modal__dot--muted {
  color: rgb(161 161 170 / 0.22);
  background: rgb(113 113 122);
}

.feed-status-modal__section {
  min-width: 0;
}

.feed-status-modal__section-heading {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.5rem;
}

.feed-status-modal__section-title {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: 0.375rem;
  margin: 0;
  color: var(--feed-status-text);
  font-size: 0.8125rem;
  font-weight: 650;
  line-height: 1.25rem;
}

.feed-status-modal__section-note {
  color: var(--feed-status-muted);
  font-size: 0.75rem;
  font-weight: 500;
  line-height: 1rem;
}

.feed-status-modal__metrics {
  display: grid;
  overflow: hidden;
  border: 1px solid var(--feed-status-line);
  border-radius: 10px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: 0;
  background: rgb(255 255 255);
}

.feed-status-modal__metrics div {
  min-width: 0;
  border-left: 1px solid var(--feed-status-line-soft);
  padding: 0.8125rem 0.875rem;
}

.feed-status-modal__metrics div:first-child {
  border-left: 0;
}

.feed-status-modal__metric-label,
.feed-status-modal__detail-list dt,
.feed-status-modal__feed-grid dt {
  color: var(--feed-status-muted);
  font-size: 0.75rem;
  line-height: 1rem;
}

.feed-status-modal__metric-value {
  min-width: 0;
  overflow-wrap: anywhere;
  margin: 0.375rem 0 0;
  color: var(--feed-status-text);
  font-feature-settings: "tnum";
  font-size: 1rem;
  font-weight: 650;
  line-height: 1.35rem;
}

.feed-status-modal__detail-list {
  overflow: hidden;
  border: 1px solid var(--feed-status-line);
  border-radius: 10px;
  margin: 0;
  background: rgb(255 255 255);
}

.feed-status-modal__detail-list div {
  display: grid;
  min-width: 0;
  grid-template-columns: 6.5rem minmax(0, 1fr);
  gap: 1rem;
  border-top: 1px solid var(--feed-status-line-soft);
  padding: 0.625rem 0.875rem;
}

.feed-status-modal__detail-list div:first-child {
  border-top: 0;
}

.feed-status-modal__detail-list dd,
.feed-status-modal__feed-grid dd {
  min-width: 0;
  overflow-wrap: anywhere;
  margin: 0;
  color: var(--feed-status-text);
  font-size: 0.8125rem;
  line-height: 1.25rem;
}

.feed-status-modal__feeds {
  overflow: hidden;
  border: 1px solid var(--feed-status-line);
  border-radius: 10px;
  background: rgb(255 255 255);
}

.feed-status-modal__feed {
  min-width: 0;
  border-top: 1px solid var(--feed-status-line-soft);
  padding: 0.875rem;
}

.feed-status-modal__feed:first-child,
.feed-status-modal__attention-row:first-child {
  border-top: 0;
}

.feed-status-modal__feed-header {
  display: grid;
  min-width: 0;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.625rem;
}

.feed-status-modal__feed-url,
.feed-status-modal__attention-title {
  min-width: 0;
  overflow-wrap: anywhere;
  color: var(--feed-status-text);
  font-size: 0.8125rem;
  font-weight: 600;
  line-height: 1.25rem;
}

.feed-status-modal__feed-label,
.feed-status-modal__attention-label {
  color: var(--feed-status-muted);
  font-size: 0.75rem;
  line-height: 1rem;
}

.feed-status-modal__feed-grid {
  display: grid;
  overflow: hidden;
  border: 1px solid var(--feed-status-line-soft);
  border-radius: 8px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 0.75rem 0 0;
  background: var(--feed-status-surface);
}

.feed-status-modal__feed-grid div {
  min-width: 0;
  border-left: 1px solid var(--feed-status-line-soft);
  border-top: 1px solid var(--feed-status-line-soft);
  padding: 0.5625rem 0.625rem;
}

.feed-status-modal__feed-grid div:nth-child(3n + 1) {
  border-left: 0;
}

.feed-status-modal__feed-grid div:nth-child(-n + 3) {
  border-top: 0;
}

.feed-status-modal__attention-row {
  display: grid;
  min-width: 0;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.75rem;
  border-top: 1px solid var(--feed-status-line-soft);
  padding: 0.875rem;
}

.feed-status-modal__attention-copy {
  min-width: 0;
}

.feed-status-modal__empty {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  border: 1px solid var(--feed-status-line);
  border-radius: 10px;
  background: rgb(255 255 255);
  padding: 0.875rem;
}

@media (max-width: 767px) {
  .feed-status-modal__summary {
    align-items: start;
  }

  .feed-status-modal__metrics,
  .feed-status-modal__feed-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .feed-status-modal__metrics div,
  .feed-status-modal__feed-grid div,
  .feed-status-modal__feed-grid div:nth-child(3n + 1),
  .feed-status-modal__feed-grid div:nth-child(-n + 3) {
    border-left: 0;
    border-top: 1px solid var(--feed-status-line-soft);
  }

  .feed-status-modal__metrics div:first-child,
  .feed-status-modal__feed-grid div:first-child {
    border-top: 0;
  }

  .feed-status-modal__detail-list div,
  .feed-status-modal__attention-row,
  .feed-status-modal__feed-header {
    grid-template-columns: minmax(0, 1fr);
  }

  .feed-status-modal__dot {
    margin-top: 0.375rem;
  }
}
</style>
