<script lang="ts" setup>
import type { LinkFeedItem } from "@/api/generated";
import { useLinkFeedItemActions } from "@/composables/use-link-feed-item-actions";
import { IconExternalLinkLine, VButton } from "@halo-dev/components";
import { utils } from "@halo-dev/ui-shared";
import { computed } from "vue";
import CalendarTimeAddFillIcon from "~icons/mingcute/calendar-time-add-fill?width=unset&height=unset";
import CalendarTimeAddLineIcon from "~icons/mingcute/calendar-time-add-line?width=unset&height=unset";
import MailLineIcon from "~icons/mingcute/mail-line?width=unset&height=unset";
import MailOpenLineIcon from "~icons/mingcute/mail-open-line?width=unset&height=unset";
import StarFillIcon from "~icons/mingcute/star-fill?width=unset&height=unset";
import StarLineIcon from "~icons/mingcute/star-line?width=unset&height=unset";

const props = withDefaults(
  defineProps<{
    item: LinkFeedItem;
    sourceName: string;
    compact?: boolean;
    itemActionMode?: "all" | "favorite-only";
  }>(),
  {
    itemActionMode: "all",
  },
);

function articleTitle(item: LinkFeedItem) {
  return item.title || item.url || "未命名文章";
}

const publishedAt = computed(() => {
  return props.item.publishedAt || props.item.updatedAt || props.item.fetchedAt;
});

const readActionLabel = computed(() => {
  return props.item.read ? "标为未读" : "标为已读";
});

const { isMarkingFavorite, isMarkingRead, isMarkingReadLater, openItem, toggleFavorite, toggleRead, toggleReadLater } =
  useLinkFeedItemActions(() => props.item);
</script>

<template>
  <article
    class=":uno: feed-item"
    :class="{
      ':uno: feed-item--read': item.read,
      ':uno: feed-item--compact': compact,
    }"
  >
    <div class=":uno: feed-item__rail">
      <span class=":uno: feed-item__dot" :class="{ ':uno: feed-item__dot--read': item.read }"></span>
    </div>

    <div class=":uno: feed-item__content">
      <div class=":uno: feed-item__header">
        <div class=":uno: feed-item__meta">
          <span class=":uno: feed-item__source">{{ sourceName }}</span>
          <span v-if="publishedAt" v-tooltip="utils.date.format(publishedAt)">
            {{ utils.date.timeAgo(publishedAt) }}
          </span>
        </div>

        <div class=":uno: feed-item__actions">
          <VButton
            size="sm"
            ghost
            class=":uno: feed-item__action"
            :aria-label="item.favorite ? '取消收藏' : '收藏'"
            v-tooltip="{
              content: item.favorite ? '取消收藏' : '收藏',
            }"
            @click="toggleFavorite()"
            :disabled="isMarkingFavorite"
          >
            <StarFillIcon v-if="item.favorite" class=":uno: feed-item__action-icon feed-item__action-icon--favorite" />
            <StarLineIcon v-else class=":uno: feed-item__action-icon" />
          </VButton>
          <VButton
            v-if="itemActionMode === 'all'"
            size="sm"
            ghost
            class=":uno: feed-item__action"
            :aria-label="item.readLater ? '移出稍后阅读' : '稍后阅读'"
            :disabled="isMarkingReadLater"
            v-tooltip="{
              content: item.readLater ? '移出稍后阅读' : '稍后阅读',
            }"
            @click="toggleReadLater()"
          >
            <CalendarTimeAddFillIcon
              v-if="item.readLater"
              class=":uno: feed-item__action-icon feed-item__action-icon--read-later"
            />
            <CalendarTimeAddLineIcon v-else class=":uno: feed-item__action-icon" />
          </VButton>
          <VButton
            v-if="itemActionMode === 'all'"
            size="sm"
            ghost
            class=":uno: feed-item__action"
            :aria-label="readActionLabel"
            :disabled="isMarkingRead"
            v-tooltip="{
              content: readActionLabel,
            }"
            @click="toggleRead()"
          >
            <MailOpenLineIcon v-if="item.read" class=":uno: feed-item__action-icon" />
            <MailLineIcon v-else class=":uno: feed-item__action-icon feed-item__action-icon--unread" />
          </VButton>
        </div>
      </div>

      <div class=":uno: feed-item__body">
        <a
          v-if="item.url"
          :href="item.url"
          target="_blank"
          rel="noopener noreferrer"
          class=":uno: feed-item__title feed-item__title--link"
          @click="openItem()"
        >
          <span>{{ articleTitle(item) }}</span>
          <IconExternalLinkLine class=":uno: feed-item__external-icon" />
        </a>
        <div v-else class=":uno: feed-item__title">
          <span>{{ articleTitle(item) }}</span>
        </div>

        <p v-if="item.summary && !compact" class=":uno: feed-item__summary">
          {{ item.summary }}
        </p>

        <div class=":uno: feed-item__badges">
          <span class=":uno: feed-item__badge" :class="{ ':uno: feed-item__badge--unread': !item.read }">
            {{ item.read ? "已读" : "未读" }}
          </span>
          <span v-if="item.favorite" class=":uno: feed-item__badge feed-item__badge--favorite">已收藏</span>
          <span v-if="item.readLater" class=":uno: feed-item__badge feed-item__badge--read-later">稍后阅读</span>
        </div>
      </div>
    </div>
  </article>
</template>

<style scoped>
.feed-item {
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 0.875rem;
  padding: 1rem;
  transition:
    background-color 0.18s ease,
    box-shadow 0.18s ease;
}

.feed-item + .feed-item {
  border-top: 1px solid rgb(244 244 245);
}

.feed-item:hover {
  background: rgb(250 250 250);
}

.feed-item--read {
  background: rgb(250 250 250 / 0.72);
}

.feed-item--compact {
  padding: 0.75rem;
}

.feed-item__rail {
  display: flex;
  justify-content: center;
  padding-top: 0.3125rem;
}

.feed-item__dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 999px;
  background: rgb(22 163 74);
  box-shadow: 0 0 0 4px rgb(22 163 74 / 0.1);
}

.feed-item__dot--read {
  background: rgb(212 212 216);
  box-shadow: none;
}

.feed-item__content {
  min-width: 0;
}

.feed-item__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
}

.feed-item__meta {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.375rem;
  color: rgb(113 113 122);
  font-size: 0.75rem;
  line-height: 1rem;
}

.feed-item__source {
  min-width: 0;
  max-width: 14rem;
  overflow: hidden;
  color: rgb(63 63 70);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feed-item__meta span {
  white-space: nowrap;
}

.feed-item__actions {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.25rem;
}

.feed-item__action-icon {
  width: 100%;
  height: 100%;
  color: rgb(82 82 91);
}

.feed-item__action-icon--favorite {
  color: rgb(202 138 4);
}

.feed-item__action-icon--read-later {
  color: rgb(37 99 235 / 0.78);
}

.feed-item__action-icon--unread {
  color: rgb(22 163 74);
}

.feed-item__body {
  min-width: 0;
  padding-top: 0.375rem;
}

.feed-item__title {
  display: inline-flex;
  max-width: 100%;
  min-width: 0;
  align-items: center;
  gap: 0.375rem;
  overflow: hidden;
  color: rgb(24 24 27);
  font-size: 0.9375rem;
  font-weight: 650;
  line-height: 1.375rem;
  text-decoration: none;
}

.feed-item__title span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feed-item__title--link:hover {
  color: rgb(0 112 243);
}

.feed-item--read .feed-item__title {
  color: rgb(82 82 91);
}

.feed-item__external-icon {
  width: 0.875rem;
  height: 0.875rem;
  flex: none;
  opacity: 0.55;
}

.feed-item__summary {
  display: -webkit-box;
  overflow: hidden;
  margin: 0.5rem 0 0;
  color: rgb(82 82 91);
  font-size: 0.875rem;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.feed-item--read .feed-item__summary {
  color: rgb(113 113 122);
}

.feed-item__badges {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
  margin-top: 0.75rem;
}

.feed-item__badge {
  display: inline-flex;
  align-items: center;
  border: 1px solid rgb(229 231 235);
  border-radius: 999px;
  background: rgb(250 250 250);
  color: rgb(82 82 91);
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1rem;
  padding: 0.1875rem 0.5rem;
}

.feed-item__badge--unread {
  border-color: rgb(187 247 208);
  background: rgb(240 253 244);
  color: rgb(21 128 61);
}

.feed-item__badge--favorite {
  border-color: rgb(254 240 138);
  background: rgb(254 252 232);
  color: rgb(161 98 7);
}

.feed-item__badge--read-later {
  border-color: rgb(219 234 254);
  background: rgb(239 246 255 / 0.72);
  color: rgb(30 64 175);
}

@media (max-width: 767px) {
  .feed-item {
    grid-template-columns: minmax(0, 1fr);
    gap: 0.625rem;
  }

  .feed-item__rail {
    display: none;
  }

  .feed-item__header {
    align-items: center;
    gap: 0.5rem;
  }

  .feed-item__meta {
    flex: 1;
    flex-wrap: nowrap;
  }

  .feed-item__source {
    flex: 1 1 auto;
  }

  .feed-item__actions {
    justify-content: flex-end;
  }
}
</style>
