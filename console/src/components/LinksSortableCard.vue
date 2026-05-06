<script lang="ts" setup>
import { linksCoreApiClient } from "@/api";
import { ApiPluginHaloRunV1alpha1LinkApiListLinksRequest, Link, LinkGroup } from "@/api/generated";
import { QK_LINKS } from "@/composables/use-link";
import { paginate } from "@halo-dev/api-client";
import { Toast, VButton, VCard, VLoading, VSpace } from "@halo-dev/components";
import { useQueryClient } from "@tanstack/vue-query";
import { onMounted, ref } from "vue";
import { VueDraggable } from "vue-draggable-plus";
import LinkBadge from "./LinkBadge.vue";

const props = defineProps<{
  group?: LinkGroup;
}>();

const emit = defineEmits<{
  (event: "close"): void;
}>();

const queryClient = useQueryClient();

const links = ref<Link[]>([]);
const isLoading = ref(false);

const isSubmitting = ref(false);

async function fetchLinks() {
  isLoading.value = true;
  try {
    const data = await paginate<ApiPluginHaloRunV1alpha1LinkApiListLinksRequest, Link>(
      (params) => linksCoreApiClient.link.listLink(params),
      {
        fieldSelector: [`spec.groupName=${props.group?.metadata.name}`],
        size: 1000,
        sort: ["spec.priority,asc"],
      },
    );

    links.value = data;
  } finally {
    isLoading.value = false;
  }
}

onMounted(() => {
  fetchLinks();
});

async function handleSave() {
  isSubmitting.value = true;
  try {
    for (const [index, link] of links.value.entries()) {
      await linksCoreApiClient.link.patchLink({
        name: link.metadata.name,
        jsonPatchInner: [{ op: "add", path: "/spec/priority", value: index + 1 }],
      });
    }
    Toast.success("保存成功");
    emit("close");
    queryClient.invalidateQueries({ queryKey: [QK_LINKS] });
  } finally {
    isSubmitting.value = false;
  }
}
</script>
<template>
  <VCard :title="group?.spec?.displayName || '未分组'">
    <template #header>
      <div class=":uno: group h-12 w-full flex items-center justify-between px-4">
        <div class=":uno: flex items-center gap-3">
          <div class=":uno: text-sm text-gray-900 font-semibold">
            {{ group?.spec?.displayName || "未分组" }}
          </div>
          <VSpace>
            <VButton size="sm" type="secondary" :loading="isSubmitting" @click="handleSave">保存</VButton>
            <VButton size="sm" @click="emit('close')">取消</VButton>
          </VSpace>
        </div>
      </div>
    </template>
    <VLoading v-if="isLoading" />
    <VueDraggable v-model="links" class=":uno: flex flex-wrap gap-2" v-else>
      <LinkBadge v-for="link in links" :key="link.metadata.name" :link="link" sort-mode />
    </VueDraggable>
  </VCard>
</template>
