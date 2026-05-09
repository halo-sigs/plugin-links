<script lang="ts" setup>
import { linksCoreApiClient } from "@/api";
import { Link } from "@/api/generated";
import { GroupWithLinks, QK_GROUPS_WITH_LINKS } from "@/composables/use-link-fetch";
import { Toast, VButton, VCard, VSpace } from "@halo-dev/components";
import { useQueryClient } from "@tanstack/vue-query";
import { chunk } from "es-toolkit";
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
    const batches = chunk(links.value, 5);
    for (const [batchIndex, batch] of batches.entries()) {
      await Promise.all(
        batch.map((link, index) =>
          linksCoreApiClient.link.patchLink({
            name: link.metadata.name,
            jsonPatchInner: [{ op: "add", path: "/spec/priority", value: batchIndex * 5 + index + 1 }],
          }),
        ),
      );
    }
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
            {{ groupWithLinks.group?.spec?.displayName || "未分组" }}
          </div>
          <VSpace>
            <VButton size="sm" type="secondary" :loading="isSubmitting" @click="handleSave">保存</VButton>
            <VButton size="sm" @click="emit('close')">取消</VButton>
          </VSpace>
        </div>
      </div>
    </template>
    <VueDraggable v-model="links" class=":uno: flex flex-wrap gap-2">
      <LinkBadge v-for="link in links" :key="link.metadata.name" :link="link" sort-mode />
    </VueDraggable>
  </VCard>
</template>
