<script lang="ts" setup>
import { linksConsoleApiClient } from "@/api";
import type { Link } from "@/api/generated";
import type { GroupWithLinks } from "@/composables/use-link-fetch";
import { QK_GROUPS_WITH_LINKS } from "@/composables/use-link-fetch";
import { Toast, VButton, VCard, VSpace } from "@halo-dev/components";
import { useQueryClient } from "@tanstack/vue-query";
import { onMounted, ref, toRaw } from "vue";
import { VueDraggable } from "vue-draggable-plus";
import LinkBadge from "./LinkBadge.vue";

const props = defineProps<{
  groupWithLinks: GroupWithLinks;
}>();

const emit = defineEmits<{
  (event: "close"): void;
}>();

const queryClient = useQueryClient();

const links = ref<Link[]>([]);

const isSubmitting = ref(false);

onMounted(() => {
  links.value = toRaw(props.groupWithLinks.links);
});

async function handleSave() {
  isSubmitting.value = true;
  try {
    const names = links.value.map((link) => link.metadata.name);
    await linksConsoleApiClient.link.sortLinks({
      sortRequest: { names },
    });
    Toast.success("保存成功");
    emit("close");
    queryClient.invalidateQueries({ queryKey: [QK_GROUPS_WITH_LINKS] });
  } finally {
    isSubmitting.value = false;
  }
}
</script>
<template>
  <VCard :title="groupWithLinks.group?.spec?.displayName || '未分组'">
    <template #header>
      <div class=":uno: group w-full flex flex-wrap items-center justify-between gap-2 px-4 py-2">
        <div class=":uno: flex flex-wrap items-center gap-3">
          <div class=":uno: text-sm text-gray-900 font-semibold">
            {{ groupWithLinks.group?.spec?.displayName || "未分组" }}（{{ links.length }}）
          </div>
          <VSpace>
            <VButton size="sm" type="secondary" :loading="isSubmitting" @click="handleSave">保存</VButton>
            <VButton size="sm" @click="emit('close')">取消</VButton>
          </VSpace>
        </div>
      </div>
    </template>
    <VueDraggable
      v-model="links"
      class=":uno: grid grid-cols-1 gap-2.5 2xl:grid-cols-5 3xl:grid-cols-6 4xl:grid-cols-7 5xl:grid-cols-8 lg:grid-cols-3 sm:grid-cols-2 xl:grid-cols-4"
    >
      <LinkBadge v-for="link in links" :key="link.metadata.name" :link="link" sort-mode />
    </VueDraggable>
  </VCard>
</template>
