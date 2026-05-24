<script lang="ts" setup>
import type { LinkFeedReadStatus } from "@/composables/use-link-feed";

defineProps<{
  selectedStatus: LinkFeedReadStatus;
}>();

const emit = defineEmits<{
  (event: "select", status: LinkFeedReadStatus): void;
}>();

const tabs: { label: string; value: LinkFeedReadStatus }[] = [
  {
    label: "全部",
    value: "",
  },
  {
    label: "未读",
    value: "unread",
  },
  {
    label: "已读",
    value: "read",
  },
];
</script>

<template>
  <div class=":uno: feed-status-tabs">
    <button
      v-for="tab in tabs"
      :key="tab.value || 'all'"
      type="button"
      class=":uno: feed-status-tabs__item"
      :class="{
        ':uno: feed-status-tabs__item--active': selectedStatus === tab.value,
      }"
      :aria-pressed="selectedStatus === tab.value"
      @click="emit('select', tab.value)"
    >
      {{ tab.label }}
    </button>
  </div>
</template>

<style scoped>
.feed-status-tabs {
  display: inline-flex;
  flex: none;
  gap: 0.25rem;
  border: 1px solid rgb(229 231 235);
  border-radius: 8px;
  background: rgb(250 250 250);
  padding: 0.25rem;
}

.feed-status-tabs__item {
  display: inline-flex;
  min-width: 3.5rem;
  height: 2rem;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: rgb(82 82 91);
  font-size: 0.875rem;
  line-height: 1.25rem;
  transition:
    background-color 0.18s ease,
    box-shadow 0.18s ease,
    color 0.18s ease;
}

.feed-status-tabs__item:hover {
  color: rgb(24 24 27);
}

.feed-status-tabs__item--active {
  background: rgb(24 24 27);
  color: rgb(250 250 250);
  box-shadow: 0 1px 2px rgb(15 23 42 / 0.16);
}

.feed-status-tabs__item--active:hover {
  color: rgb(250 250 250);
}
</style>
