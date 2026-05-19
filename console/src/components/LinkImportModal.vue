<script lang="ts" setup>
import { linksCoreApiClient } from "@/api";
import { LinkGroup } from "@/api/generated";
import { QK_GROUPS_WITH_LINKS } from "@/composables/use-link-fetch";
import { Toast, VModal } from "@halo-dev/components";
import { useQueryClient } from "@tanstack/vue-query";
import { chunk } from "es-toolkit";
import { ref, useTemplateRef } from "vue";
import LinkImportBody, { type ParsedItem } from "./LinkImportBody.vue";

const props = defineProps<{
  group?: LinkGroup;
}>();

const emit = defineEmits<{
  (event: "close"): void;
}>();

const queryClient = useQueryClient();
const modal = useTemplateRef<InstanceType<typeof VModal> | null>("modal");

const isImporting = ref(false);

async function handleSubmit(data: { items: ParsedItem[]; groupName: string }) {
  const items = data.items;
  if (items.length === 0) {
    Toast.warning("没有可导入的链接");
    return;
  }

  isImporting.value = true;

  try {
    const { data: linkList } = await linksCoreApiClient.link.listLink({
      page: 1,
      size: 1,
      sort: ["spec.priority,desc"],
    });
    let maxPriority = linkList.items[0]?.spec?.priority || 0;

    const chunks = chunk(items, 5);
    for (const chunkItems of chunks) {
      await Promise.all(
        chunkItems.map((item) => {
          maxPriority++;
          return linksCoreApiClient.link.createLink({
            link: {
              apiVersion: "core.halo.run/v1alpha1",
              kind: "Link",
              spec: {
                url: item.url,
                displayName: item.displayName,
                logo: item.logo,
                description: item.description,
                groupName: data.groupName,
                priority: maxPriority,
              },
              metadata: {
                name: "",
                generateName: "link-",
              },
            },
          });
        }),
      );
    }

    Toast.success(`成功导入 ${items.length} 个链接`);
    queryClient.invalidateQueries({ queryKey: [QK_GROUPS_WITH_LINKS] });
    modal.value?.close();
  } finally {
    isImporting.value = false;
  }
}
</script>

<template>
  <VModal
    :centered="false"
    title="批量导入链接"
    ref="modal"
    :mount-to-body="true"
    :width="720"
    @close="emit('close')"
  >
    <LinkImportBody :group="group" :is-importing="isImporting" @submit="handleSubmit" />
  </VModal>
</template>
