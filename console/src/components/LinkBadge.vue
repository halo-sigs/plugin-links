<script lang="ts" setup>
import { Link } from "@/api/generated";
import { defineAsyncComponent, ref } from "vue";

const LinkEditingModal = defineAsyncComponent(
  () => import(/* webpackChunkName: "link-editing-modal" */ "./LinkEditingModal.vue"),
);

const props = defineProps<{
  selectMode?: boolean;
  sortMode?: boolean;
  link: Link;
}>();

const editingModalVisible = ref(false);

function handleClick() {
  if (!props.selectMode && !props.sortMode) {
    editingModalVisible.value = true;
  }
}
</script>
<template>
  <label
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
  </label>

  <LinkEditingModal v-if="editingModalVisible" :link="link" @close="editingModalVisible = false" />
</template>
