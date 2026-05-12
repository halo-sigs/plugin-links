<script lang="ts" setup>
import { linksConsoleApiClient, linksPublicApiClient } from "@/api";
import { LinkGroupVo } from "@/api/generated";
import { QK_LINK_GROUPS } from "@/composables/use-group-fetch";
import { QK_GROUPS_WITH_LINKS } from "@/composables/use-link-fetch";
import { Toast, VButton, VLoading, VModal, VSpace } from "@halo-dev/components";
import { useQueryClient } from "@tanstack/vue-query";
import { onMounted, ref } from "vue";
import { VueDraggable } from "vue-draggable-plus";
import RiDragMove2Line from "~icons/ri/drag-move-2-line";

const emit = defineEmits<{
  (event: "close"): void;
}>();

const queryClient = useQueryClient();

const modal = ref<InstanceType<typeof VModal> | null>(null);

const groups = ref<LinkGroupVo[]>([]);

const isLoading = ref(false);

const isSubmitting = ref(false);

async function fetchGroups() {
  isLoading.value = true;
  try {
    const { data } = await linksPublicApiClient.linkGroup.queryLinkGroups();
    groups.value = data;
  } finally {
    isLoading.value = false;
  }
}

onMounted(() => {
  fetchGroups();
});

async function handleSave() {
  isSubmitting.value = true;
  try {
    const names = groups.value.map((group) => group.metadata.name);
    await linksConsoleApiClient.link.sortLinkGroups({
      sortRequest: { names },
    });
    Toast.success("保存成功");
    modal.value?.close();
    queryClient.invalidateQueries({ queryKey: [QK_LINK_GROUPS] });
    queryClient.invalidateQueries({ queryKey: [QK_GROUPS_WITH_LINKS] });
  } finally {
    isSubmitting.value = false;
  }
}
</script>

<template>
  <VModal title="调整分组排序" ref="modal" :centered="false" :mount-to-body="true" :width="600" @close="emit('close')">
    <div>
      <VLoading v-if="isLoading" />
      <VueDraggable v-model="groups" class=":uno: rounded-base overflow-hidden border divide-y divide-gray-100">
        <div
          v-for="group in groups"
          :key="group.metadata.name"
          class=":uno: flex cursor-move items-center gap-2 px-3 py-2 text-sm text-gray-600 font-semibold transition-colors hover:text-gray-900"
        >
          <RiDragMove2Line class=":uno: size-4" />
          <div>
            {{ group.spec?.displayName }}
          </div>
        </div>
      </VueDraggable>
    </div>
    <template #footer>
      <VSpace>
        <VButton type="secondary" :loading="isSubmitting" @click="handleSave">保存</VButton>
        <VButton @click="modal?.close()">关闭</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
