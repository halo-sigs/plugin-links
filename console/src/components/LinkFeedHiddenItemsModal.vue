<script lang="ts" setup>
import type { LinkFeedItem } from "@/api/generated";
import type { LinkFeedItems } from "@/composables/use-link-feed";
import { useLinkFeedHiddenState } from "@/composables/use-link-feed-hidden-state";
import { Dialog, Toast, VButton, VModal, VSpace } from "@halo-dev/components";
import { computed, shallowRef, useTemplateRef } from "vue";
import LinkFeedItemList from "./LinkFeedItemList.vue";

const emit = defineEmits<{
  (event: "close"): void;
}>();

const modal = useTemplateRef<InstanceType<typeof VModal> | null>("modal");
const props = defineProps<{
  feed: LinkFeedItems;
  sourceName: (linkName?: string) => string;
}>();

const { isUpdating, setHiddenState } = useLinkFeedHiddenState();

const selectedIds = shallowRef<string[]>([]);
const items = computed(() => props.feed.items.value);
const isLoading = computed(() => props.feed.isLoading.value);
const selectedCount = computed(() => selectedIds.value.length);
const allLoadedSelected = computed(() => {
  return items.value.length > 0 && items.value.every((item) => item.id && selectedIds.value.includes(item.id));
});

function toggleSelect(item: LinkFeedItem) {
  if (!item.id) {
    return;
  }
  if (selectedIds.value.includes(item.id)) {
    selectedIds.value = selectedIds.value.filter((id) => id !== item.id);
  } else {
    selectedIds.value = [...selectedIds.value, item.id];
  }
}

function selectAllLoaded() {
  selectedIds.value = items.value.map((item) => item.id).filter((id): id is string => !!id);
}

function clearSelection() {
  selectedIds.value = [];
}

async function handleUnhide(item: LinkFeedItem) {
  if (!item.id || isUpdating.value) {
    return;
  }
  const result = await setHiddenState([item.id], false);
  if (!result) {
    return;
  }
  clearSelection();
  showUnhideSummary(result.updatedCount);
}

function handleBatchUnhide() {
  if (!selectedCount.value || isUpdating.value) {
    return;
  }

  Dialog.warning({
    title: "恢复所选文章",
    description: `确认恢复所选的 ${selectedCount.value} 篇已隐藏文章吗？恢复后将重新出现在正常列表和公开订阅中。`,
    confirmType: "primary",
    onConfirm: async () => {
      const result = await setHiddenState(selectedIds.value, false);
      if (!result) {
        return;
      }
      clearSelection();
      showUnhideSummary(result.updatedCount);
    },
  });
}

function showUnhideSummary(updatedCount: number) {
  if (!updatedCount) {
    Toast.info("所选文章已处于可见状态");
    return;
  }
  Toast.success(`已恢复 ${updatedCount} 篇文章`);
}

async function handleReload() {
  clearSelection();
  await props.feed.reload();
}
</script>

<template>
  <VModal ref="modal" :centered="false" title="已隐藏文章" :mount-to-body="true" :width="860" @close="emit('close')">
    <div class=":uno: hidden-feed">
      <div class=":uno: hidden-feed__toolbar">
        <span class=":uno: hidden-feed__selection" role="status">
          {{ selectedCount ? `已选 ${selectedCount} 篇` : "勾选文章后可批量恢复" }}
        </span>
        <VSpace class=":uno: flex-wrap">
          <VButton size="sm" ghost :disabled="!items.length || allLoadedSelected" @click="selectAllLoaded">
            全选已加载
          </VButton>
          <VButton size="sm" ghost :disabled="!selectedCount" @click="clearSelection">取消选择</VButton>
          <VButton size="sm" :disabled="!selectedCount" :loading="isUpdating" @click="handleBatchUnhide">
            恢复所选{{ selectedCount ? ` (${selectedCount})` : "" }}
          </VButton>
        </VSpace>
      </div>

      <LinkFeedItemList
        :feed="feed"
        :source-name="sourceName"
        empty-text="暂无已隐藏文章"
        item-action-mode="hidden"
        selectable
        :selected-ids="selectedIds"
        @toggle-select="toggleSelect"
        @unhide="handleUnhide"
      />
    </div>

    <template #footer>
      <VSpace>
        <VButton type="secondary" :loading="isLoading" @click="handleReload()">重新加载</VButton>
        <VButton @click="modal?.close()">关闭</VButton>
      </VSpace>
    </template>
  </VModal>
</template>

<style scoped>
.hidden-feed {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0.75rem;
}

.hidden-feed__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.hidden-feed__selection {
  color: rgb(113 113 122);
  font-size: 0.8125rem;
  line-height: 1.125rem;
}

@media (max-width: 767px) {
  .hidden-feed__toolbar {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
