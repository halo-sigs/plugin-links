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
  <div class=":uno: inline-flex rounded-lg bg-gray-100 p-1">
    <button
      v-for="tab in tabs"
      :key="tab.value || 'all'"
      type="button"
      class=":uno: h-8 min-w-14 rounded-md px-3 text-sm transition-colors"
      :class="{
        ':uno: bg-white text-gray-900 shadow-sm': selectedStatus === tab.value,
        ':uno: text-gray-500 hover:text-gray-900': selectedStatus !== tab.value,
      }"
      :aria-pressed="selectedStatus === tab.value"
      @click="emit('select', tab.value)"
    >
      {{ tab.label }}
    </button>
  </div>
</template>
