<script lang="ts" setup>
import type { LinkFeedItem } from "@/api/generated";
import type { LinkFeedItems } from "@/composables/use-link-feed";
import { VButton, VModal, VSpace } from "@halo-dev/components";
import { computed, useTemplateRef } from "vue";
import LinkFeedItemList from "./LinkFeedItemList.vue";

const emit = defineEmits<{
  (event: "close"): void;
}>();

const modal = useTemplateRef<InstanceType<typeof VModal> | null>("modal");
const props = defineProps<{
  title: string;
  feed: LinkFeedItems;
  sourceName: (linkName?: string) => string;
  publishedAtText: (item: LinkFeedItem) => string;
  emptyText: string;
}>();
const isLoading = computed(() => props.feed.isLoading.value);
</script>

<template>
  <VModal ref="modal" :centered="false" :title="title" :mount-to-body="true" :width="860" @close="emit('close')">
    <LinkFeedItemList
      :feed="feed"
      :source-name="sourceName"
      :published-at-text="publishedAtText"
      :empty-text="emptyText"
      scrollable
    />

    <template #footer>
      <VSpace>
        <VButton type="secondary" :loading="isLoading" @click="feed.reload()">重新加载</VButton>
        <VButton @click="modal?.close()">关闭</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
