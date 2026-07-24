<script lang="ts" setup>
import type { LinkApplication } from "@/api/generated";
import { linkApplicationSourceMeta } from "@/utils/link-application-origin";
import { VTag } from "@halo-dev/components";
import { computed } from "vue";

const props = defineProps<{
  application: LinkApplication;
}>();

const source = computed(() => linkApplicationSourceMeta(props.application));
</script>

<template>
  <VTag :type="source.tagType" size="sm">
    {{ source.label }}
  </VTag>
</template>
