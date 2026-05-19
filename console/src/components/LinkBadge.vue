<script lang="ts" setup>
import type { Link } from "@/api/generated";
import { IconExternalLinkLine } from "@halo-dev/components";

const props = defineProps<{
  selectMode?: boolean;
  sortMode?: boolean;
  link: Link;
}>();

const emit = defineEmits<{
  (event: "open-edit"): void;
}>();

function handleClick() {
  if (!props.selectMode && !props.sortMode) {
    emit("open-edit");
  }
}
</script>
<template>
  <label
    class=":uno: min-w-0 w-full inline-flex cursor-pointer items-center gap-2.5 rounded-lg bg-gray-100 px-2 py-1 transition-colors hover:bg-gray-200"
    :class="{
      ':uno: animate-flash opacity-50': link.metadata.deletionTimestamp,
      ':uno: cursor-move': sortMode,
    }"
    @click="handleClick"
  >
    <slot name="checkbox" v-if="selectMode"></slot>
    <img v-else :src="link.spec?.logo" class=":uno: size-4 flex-none shrink-0 rounded-sm" />
    <div class=":uno: min-w-0 flex flex-1 shrink flex-col">
      <span
        class=":uno: truncate text-xs text-gray-900"
        :class="{
          ':uno: line-through': link.metadata.deletionTimestamp,
        }"
        v-tooltip="{
          content: link.spec?.displayName,
          disabled: selectMode || sortMode,
        }"
      >
        {{ link.spec?.displayName }}
      </span>
      <span
        class=":uno: truncate text-xs text-gray-500"
        v-tooltip="{
          content: link.spec?.url,
          disabled: selectMode || sortMode,
        }"
      >
        {{ link.spec?.url }}
      </span>
    </div>
    <a
      v-if="!selectMode && !sortMode && link.spec?.url"
      :href="link.spec.url"
      target="_blank"
      class=":uno: flex-none opacity-40 transition-opacity hover:opacity-100"
      @click.stop
    >
      <IconExternalLinkLine class=":uno: size-3.5 text-gray-500" />
    </a>
  </label>
</template>
