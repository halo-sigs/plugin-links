<script lang="ts" setup>
import type { LinkApplication, LinkApplicationSpecStatusEnum } from "@/api/generated";
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
import {
  Dialog,
  IconExternalLinkLine,
  Toast,
  VButton,
  VDropdownDivider,
  VDropdownItem,
  VEmpty,
  VEntity,
  VEntityContainer,
  VEntityField,
  VLoading,
  VModal,
  VPagination,
  VStatusDot,
  type StatusDotState,
} from "@halo-dev/components";
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
const page = ref(1);
const size = ref(20);

watch([statusFilter, originTypeFilter], () => {
  page.value = 1;
});

const filterInput = computed(() => ({
  status: statusFilter.value,
  originType: originTypeFilter.value,
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

const statusDotStates: Record<LinkApplicationSpecStatusEnum, StatusDotState> = {
  PENDING: "warning",
  APPROVING: "default",
  APPROVED: "success",
  REJECTED: "error",
};

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
    <div>
      <div class=":uno: mb-3 flex flex-wrap items-center gap-2">
        <FilterDropdown v-model="statusFilter" label="状态" :items="statusFilterOptions" />
        <FilterDropdown v-model="originTypeFilter" label="来源" :items="originTypeFilterOptions" />
        <div class=":uno: ml-auto">
          <VButton size="sm" type="danger" ghost :disabled="!total" :loading="isCleaningUp" @click="handleCleanup">
            清理
          </VButton>
        </div>
      </div>

      <VLoading v-if="isLoading" />

      <VEmpty v-else-if="!applications.length" title="暂无符合条件的申请" />

      <div v-else class=":uno: rounded-base overflow-hidden border">
        <VEntityContainer>
          <VEntity v-for="app in applications" :key="app.metadata.name">
            <template #start>
              <VEntityField
                :title="app.spec.displayName"
                :description="app.spec.url"
                max-width="32rem"
                @click="emit('view-detail', app)"
              >
                <template #extra>
                  <a
                    :href="app.spec.url"
                    target="_blank"
                    class=":uno: text-gray-600 opacity-0 transition-all hover:text-gray-900 group-hover:opacity-100"
                    @click.stop
                  >
                    <IconExternalLinkLine class=":uno: h-3.5 w-3.5" />
                  </a>
                </template>
              </VEntityField>
            </template>

            <template #end>
              <VEntityField>
                <template #description>
                  <VStatusDot
                    :state="statusDotStates[app.spec.status]"
                    :animate="app.spec.status === 'APPROVING'"
                    :text="linkApplicationStatusMeta(app.spec.status).label"
                  />
                </template>
              </VEntityField>
              <VEntityField>
                <template #description>
                  <LinkApplicationSourceBadge :application="app" />
                </template>
              </VEntityField>
              <VEntityField
                :description="utils.date.timeAgo(app.metadata.creationTimestamp)"
                v-tooltip="utils.date.format(app.metadata.creationTimestamp)"
              >
              </VEntityField>
            </template>

            <template #dropdownItems>
              <VDropdownItem @click="emit('view-detail', app)">
                {{ app.spec.status === "APPROVING" ? "继续审批" : app.spec.status === "PENDING" ? "审核" : "查看" }}
              </VDropdownItem>
              <template v-if="app.spec.status !== 'APPROVING'">
                <VDropdownDivider />
                <VDropdownItem type="danger" @click="handleDelete(app)">删除</VDropdownItem>
              </template>
            </template>
          </VEntity>
        </VEntityContainer>
      </div>

      <div v-if="total" class=":uno: mt-4 flex justify-end">
        <VPagination v-model:page="page" v-model:size="size" :total="total" :size-options="[10, 20, 50, 100]" />
      </div>
    </div>

    <template #footer>
      <VButton @click="modal?.close()">关闭</VButton>
    </template>
  </VModal>
</template>
