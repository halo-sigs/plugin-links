<script lang="ts" setup>
import { Link } from "@/api/generated";
import RiExternalLinkLine from "~icons/ri/external-link-line";

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
    v-tooltip="link.spec?.url"
    class=":uno: inline-flex cursor-pointer items-center gap-2 rounded-lg bg-gray-100 px-2 py-1 transition-colors hover:bg-gray-200"
    :class="{
      ':uno: animate-flash opacity-50': link.metadata.deletionTimestamp,
      ':uno: cursor-move': sortMode,
    }"
    @click="handleClick"
  >
    <slot name="checkbox" v-if="selectMode"></slot>
    <img v-else :src="link.spec?.logo" class=":uno: size-4" />
    <span
      class=":uno: text-sm text-gray-900"
      :class="{
        ':uno: line-through': link.metadata.deletionTimestamp,
      }"
    >
      {{ link.spec?.displayName }}
    </span>
    <a
      v-if="!selectMode && !sortMode && link.spec?.url"
      :href="link.spec.url"
      target="_blank"
      class=":uno: ml-0.5 opacity-40 transition-opacity hover:opacity-100"
      @click.stop
    >
      <RiExternalLinkLine class=":uno: size-3 text-gray-500" />
    </a>
  </label>
</template>
