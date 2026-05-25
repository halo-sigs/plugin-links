<script lang="ts" setup>
import type { LinkFeedStatusTone } from "@/composables/link-feed-status";
import { computed } from "vue";
import Rss2FillIcon from "~icons/mingcute/rss-2-fill";

const props = defineProps<{
  description: string;
  label: string;
  tone: LinkFeedStatusTone;
}>();

const emit = defineEmits<{
  (event: "open"): void;
}>();

const tooltipContent = computed(() => `${props.label}：${props.description}。点击查看详情`);
</script>

<template>
  <button
    type="button"
    class=":uno: feed-status-badge"
    :class="`:uno: feed-status-badge--${tone}`"
    :aria-label="tooltipContent"
    v-tooltip="{
      content: tooltipContent,
    }"
    @click="emit('open')"
  >
    <Rss2FillIcon class=":uno: feed-status-badge__icon" />
  </button>
</template>

<style scoped>
.feed-status-badge {
  display: inline-flex;
  width: 1.75rem;
  height: 1.75rem;
  flex: none;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  padding: 0;
  transition:
    border-color 0.18s ease,
    background-color 0.18s ease,
    color 0.18s ease;
}

.feed-status-badge__icon {
  width: 0.9375rem;
  height: 0.9375rem;
  flex: none;
}

.feed-status-badge:hover,
.feed-status-badge:focus-visible {
  border-color: rgb(228 228 231);
  background: rgb(244 244 245);
}

.feed-status-badge--success {
  color: rgb(22 163 74);
}

.feed-status-badge--warning {
  color: rgb(202 138 4);
}

.feed-status-badge--danger {
  color: rgb(220 38 38);
}

.feed-status-badge--muted {
  color: rgb(113 113 122);
}
</style>
