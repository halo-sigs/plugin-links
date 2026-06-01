<script lang="ts" setup>
import { useDeleteLinkApplication, useLinkApplications } from "@/composables/use-link-application";
import type { LinkApplication } from "@/api/generated";
import { Dialog, Toast, VButton, VEmpty, VModal, VSpace } from "@halo-dev/components";
import { ref, computed } from "vue";

const emit = defineEmits<{
  (event: "close"): void;
  (event: "view-detail", application: LinkApplication): void;
}>();

const { data: applications, isLoading } = useLinkApplications("PENDING");

const { mutate: deleteApplication } = useDeleteLinkApplication();

const selectedApplication = ref<LinkApplication | null>(null);

const pendingCount = computed(() => applications.value?.length || 0);

function formatDate(dateStr: string): string {
  if (!dateStr) return "";
  return new Date(dateStr).toLocaleString("zh-CN");
}

function handleDelete(app: LinkApplication) {
  Dialog.warning({
    title: "确认删除申请？",
    description: `删除后将无法恢复，确认删除 "${app.spec.displayName}" 的申请吗？`,
    confirmType: "danger",
    onConfirm: () => {
      deleteApplication(app.metadata.name, {
        onSuccess: () => {
          Toast.success("删除成功");
        },
      });
    },
  });
}
</script>

<template>
  <VModal title="友链申请" :width="800" :mount-to-body="true" @close="emit('close')">
    <div class=":uno: mb-3 text-sm text-gray-500">
      当前有 <span class=":uno: font-medium text-gray-900">{{ pendingCount }}</span> 条待审核申请
    </div>

    <VLoading v-if="isLoading" />

    <VEmpty v-else-if="!applications?.length" title="暂无待审核申请" />

    <div v-else class=":uno: overflow-x-auto">
      <table class=":uno: min-w-full divide-y divide-gray-200">
        <thead class=":uno: bg-gray-50">
          <tr>
            <th class=":uno: px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">网站名称</th>
            <th class=":uno: px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">链接地址</th>
            <th class=":uno: px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">申请时间</th>
            <th class=":uno: px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">操作</th>
          </tr>
        </thead>
        <tbody class=":uno: bg-white divide-y divide-gray-200">
          <tr v-for="app in applications" :key="app.metadata.name">
            <td class=":uno: px-4 py-2 text-sm text-gray-900">{{ app.spec.displayName }}</td>
            <td class=":uno: px-4 py-2 text-sm">
              <a :href="app.spec.url" target="_blank" class=":uno: text-blue-600 hover:underline">
                {{ app.spec.url }}
              </a>
            </td>
            <td class=":uno: px-4 py-2 text-sm text-gray-500">{{ app.metadata.creationTimestamp ? formatDate(app.metadata.creationTimestamp) : '-' }}</td>
            <td class=":uno: px-4 py-2 text-sm">
              <VSpace>
                <VButton size="xs" type="secondary" @click="app.metadata.name && emit('view-detail', app)">
                  审核
                </VButton>
                <VButton size="xs" type="danger" @click="handleDelete(app)">
                  删除
                </VButton>
              </VSpace>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </VModal>
</template>
