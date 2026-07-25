<script lang="ts" setup>
import type { LinkApplication } from "@/api/generated";
import {
  useCleanupLinkApplications,
  useDeleteLinkApplication,
  useLinkApplications,
} from "@/composables/use-link-application";
import {
  buildLinkApplicationCleanupParams,
  buildLinkApplicationQuery,
  linkApplicationCleanupDescription,
  linkApplicationCleanupSummary,
  linkApplicationStatusMeta,
} from "@/utils/link-application-review";
import { Dialog, Toast, VButton, VEmpty, VLoading, VModal, VPagination, VSpace, VTag } from "@halo-dev/components";
import { utils } from "@halo-dev/ui-shared";
import { computed, ref, useTemplateRef, watch } from "vue";
import LinkApplicationSourceBadge from "./LinkApplicationSourceBadge.vue";

const props = withDefaults(
  defineProps<{
    initialStatus?: string;
  }>(),
  {
    initialStatus: "PENDING",
  },
);

const emit = defineEmits<{
  (event: "close"): void;
  (event: "view-detail", application: LinkApplication): void;
}>();

const modal = useTemplateRef<InstanceType<typeof VModal> | null>("modal");

const statusFilter = ref(props.initialStatus);
const originTypeFilter = ref("");
const createdAfterDate = ref("");
const createdBeforeDate = ref("");
const page = ref(1);
const size = ref(20);

watch([statusFilter, originTypeFilter, createdAfterDate, createdBeforeDate], () => {
  page.value = 1;
});

const filterInput = computed(() => ({
  status: statusFilter.value,
  originType: originTypeFilter.value,
  createdAfterDate: createdAfterDate.value,
  createdBeforeDate: createdBeforeDate.value,
}));

const queryParams = computed(() =>
  buildLinkApplicationQuery(filterInput.value, { page: page.value, size: size.value }),
);

const { data: applicationsPage, isLoading } = useLinkApplications(queryParams);

const applications = computed(() => applicationsPage.value?.items || []);
const total = computed(() => applicationsPage.value?.total || 0);

const { mutate: deleteApplication } = useDeleteLinkApplication();
const { mutateAsync: cleanupApplications, isPending: isCleaningUp } = useCleanupLinkApplications();

const statusFilterOptions = [
  { label: "全部状态", value: "" },
  { label: "待审核", value: "PENDING" },
  { label: "审批中", value: "APPROVING" },
  { label: "已通过", value: "APPROVED" },
  { label: "已拒绝", value: "REJECTED" },
];

const originTypeFilterOptions = [
  { label: "全部来源", value: "" },
  { label: "表单申请", value: "FORM" },
  { label: "评论识别", value: "COMMENT" },
];

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

function handleCleanup() {
  if (!total.value) {
    return;
  }
  Dialog.warning({
    title: "确认清理当前筛选结果？",
    description: linkApplicationCleanupDescription({
      total: total.value,
      status: statusFilter.value || undefined,
      originType: originTypeFilter.value || undefined,
    }),
    confirmType: "danger",
    onConfirm: async () => {
      try {
        const result = await cleanupApplications(buildLinkApplicationCleanupParams(filterInput.value));
        const summary = linkApplicationCleanupSummary(result);
        if ((result.failed ?? 0) > 0) {
          Toast.warning(summary);
        } else {
          Toast.success(summary);
        }
        page.value = 1;
      } catch {
        // HTTP failures are reported by the global error interceptor.
      }
    },
  });
}
</script>

<template>
  <VModal ref="modal" title="友链申请" :width="900" :mount-to-body="true" :centered="false" @close="emit('close')">
    <div class=":uno: mb-3 flex flex-wrap items-center gap-2">
      <FilterDropdown v-model="statusFilter" label="状态" :items="statusFilterOptions" />
      <FilterDropdown v-model="originTypeFilter" label="来源" :items="originTypeFilterOptions" />
      <input
        v-model="createdAfterDate"
        type="date"
        aria-label="申请时间起始"
        class=":uno: h-8 border border-gray-300 rounded px-2 text-sm text-gray-700"
      />
      <span class=":uno: text-sm text-gray-400">至</span>
      <input
        v-model="createdBeforeDate"
        type="date"
        aria-label="申请时间截止"
        class=":uno: h-8 border border-gray-300 rounded px-2 text-sm text-gray-700"
      />
      <div class=":uno: ml-auto">
        <VButton size="sm" type="danger" ghost :disabled="!total" :loading="isCleaningUp" @click="handleCleanup">
          清理
        </VButton>
      </div>
    </div>

    <div class=":uno: mb-3 text-sm text-gray-500">共 {{ total }} 条记录</div>

    <VLoading v-if="isLoading" />

    <VEmpty v-else-if="!applications.length" title="暂无符合条件的申请" />

    <div v-else class=":uno: overflow-x-auto">
      <table class=":uno: min-w-full divide-y divide-gray-200">
        <thead class=":uno: bg-gray-50">
          <tr>
            <th class=":uno: px-4 py-2 text-left text-xs text-gray-500 font-medium tracking-wider uppercase">
              网站名称
            </th>
            <th class=":uno: px-4 py-2 text-left text-xs text-gray-500 font-medium tracking-wider uppercase">
              链接地址
            </th>
            <th class=":uno: px-4 py-2 text-left text-xs text-gray-500 font-medium tracking-wider uppercase">状态</th>
            <th class=":uno: px-4 py-2 text-left text-xs text-gray-500 font-medium tracking-wider uppercase">来源</th>
            <th class=":uno: px-4 py-2 text-left text-xs text-gray-500 font-medium tracking-wider uppercase">
              申请时间
            </th>
            <th class=":uno: px-4 py-2 text-left text-xs text-gray-500 font-medium tracking-wider uppercase">操作</th>
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
            <td class=":uno: px-4 py-2 text-sm">
              <VTag :type="linkApplicationStatusMeta(app.spec.status).tagType" size="sm">
                {{ linkApplicationStatusMeta(app.spec.status).label }}
              </VTag>
            </td>
            <td class=":uno: px-4 py-2 text-sm">
              <LinkApplicationSourceBadge :application="app" />
            </td>
            <td class=":uno: px-4 py-2 text-sm text-gray-500">
              {{ app.metadata.creationTimestamp ? utils.date.format(app.metadata.creationTimestamp) : "-" }}
            </td>
            <td class=":uno: px-4 py-2 text-sm">
              <VSpace>
                <VButton
                  v-if="app.spec.status === 'APPROVING'"
                  size="xs"
                  type="secondary"
                  @click="emit('view-detail', app)"
                >
                  继续审批
                </VButton>
                <template v-else>
                  <VButton size="xs" type="secondary" @click="emit('view-detail', app)">
                    {{ app.spec.status === "PENDING" ? "审核" : "查看" }}
                  </VButton>
                  <VButton size="xs" type="danger" @click="handleDelete(app)"> 删除 </VButton>
                </template>
              </VSpace>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="total" class=":uno: mt-4 flex justify-end">
      <VPagination v-model:page="page" v-model:size="size" :total="total" :size-options="[10, 20, 50, 100]" />
    </div>

    <template #footer>
      <VButton @click="modal?.close()">关闭</VButton>
    </template>
  </VModal>
</template>
