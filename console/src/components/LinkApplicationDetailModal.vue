<script lang="ts" setup>
import type { ApproveRequest, LinkApplication } from "@/api/generated";
import { QK_LINK_GROUPS, useLinkGroupFetch } from "@/composables/use-group-fetch";
import {
  useApproveLinkApplication,
  useDeleteLinkApplication,
  useRejectLinkApplication,
  useVerifyBacklink,
} from "@/composables/use-link-application";
import { QK_GROUPS_WITH_LINKS, QK_RSS_GROUPS_WITH_LINKS } from "@/composables/use-link-fetch";
import {
  buildLinkApplicationApprovalRequest,
  canVerifyLinkApplicationBacklink,
  linkApplicationEffectiveFields,
  linkApplicationRejectDescription,
  linkApplicationReviewMode,
  linkApplicationStatusMeta,
} from "@/utils/link-application-review";
import { Dialog, Toast, VAlert, VButton, VModal, VSpace, VTag } from "@halo-dev/components";
import { useQueryClient } from "@tanstack/vue-query";
import { computed, useTemplateRef } from "vue";
import LinkApplicationOriginDetails from "./LinkApplicationOriginDetails.vue";
import LinkApplicationSourceBadge from "./LinkApplicationSourceBadge.vue";

const props = defineProps<{
  application: LinkApplication;
}>();

const emit = defineEmits<{
  (event: "close"): void;
}>();

const queryClient = useQueryClient();

const modal = useTemplateRef<InstanceType<typeof VModal> | null>("modal");

const { data: groups } = useLinkGroupFetch();

const { mutate: approveApplication, isPending: isApproving } = useApproveLinkApplication();
const { mutate: rejectApplication, isPending: isRejecting } = useRejectLinkApplication();
const { mutate: deleteApplication } = useDeleteLinkApplication();
const { mutate: verifyApplication, data: verifyResult } = useVerifyBacklink();

const reviewMode = computed(() => linkApplicationReviewMode(props.application));
const statusMeta = computed(() => linkApplicationStatusMeta(props.application.spec.status));
const frozenFields = computed(() => linkApplicationEffectiveFields(props.application));
const backlinkVerificationAvailable = computed(() => canVerifyLinkApplicationBacklink(props.application));

const modalTitle = computed(() =>
  reviewMode.value === "editable"
    ? `审核申请 - ${props.application.spec.displayName}`
    : `申请详情 - ${props.application.spec.displayName}`,
);

const frozenGroupLabel = computed(() => {
  const groupName = frozenFields.value.groupName;
  if (!groupName) {
    return "不分配";
  }
  const group = groups.value?.find((item) => item.metadata?.name === groupName);
  return group?.spec?.displayName || groupName;
});

const groupOptions = computed(() => [
  { value: "", label: "不分配" },
  ...(groups.value || []).flatMap((group) => {
    const name = group.metadata?.name;
    if (!name) {
      return [];
    }
    return {
      value: name,
      label: group.spec?.displayName || name,
    };
  }),
]);

function handleApproveSuccess() {
  Toast.success("已通过申请");
  queryClient.invalidateQueries({ queryKey: [QK_GROUPS_WITH_LINKS] });
  queryClient.invalidateQueries({ queryKey: [QK_RSS_GROUPS_WITH_LINKS] });
  queryClient.invalidateQueries({ queryKey: [QK_LINK_GROUPS] });
  modal.value?.close();
}

function handleApprove(data: ApproveRequest) {
  if (!data.displayName?.trim()) {
    Toast.error("网站名称不能为空");
    return;
  }
  if (!data.url?.trim()) {
    Toast.error("链接地址不能为空");
    return;
  }
  approveApplication(
    {
      name: props.application.metadata.name,
      request: buildLinkApplicationApprovalRequest(data),
    },
    {
      onSuccess: handleApproveSuccess,
    },
  );
}

function handleResumeApproval() {
  approveApplication(
    {
      name: props.application.metadata.name,
    },
    {
      onSuccess: handleApproveSuccess,
    },
  );
}

function handleReject() {
  Dialog.warning({
    title: "确认拒绝申请？",
    description: linkApplicationRejectDescription(props.application),
    confirmType: "danger",
    onConfirm: () => {
      rejectApplication(props.application.metadata.name, {
        onSuccess: () => {
          Toast.success("已拒绝申请");
          modal.value?.close();
        },
      });
    },
  });
}

function handleVerify() {
  const name = props.application.metadata.name;
  if (!name) return;
  verifyApplication(name, {
    onSuccess: (result) => {
      if (result.found) {
        Toast.success(result.message || "反链验证通过");
      } else {
        Toast.warning(result.message || "反链验证未通过");
      }
    },
  });
}

function handleDelete() {
  Dialog.warning({
    title: "确认删除申请？",
    description: "删除后将无法恢复，确认继续吗？",
    confirmType: "danger",
    onConfirm: () => {
      deleteApplication(props.application.metadata.name, {
        onSuccess: () => {
          Toast.success("删除成功");
          modal.value?.close();
        },
      });
    },
  });
}
</script>

<template>
  <VModal ref="modal" :title="modalTitle" :width="600" :mount-to-body="true" :centered="false" @close="emit('close')">
    <div class=":uno: space-y-4">
      <!-- Status -->
      <div class=":uno: flex items-center gap-2">
        <span class=":uno: text-sm text-gray-500">状态：</span>
        <VTag :type="statusMeta.tagType" size="sm">
          {{ statusMeta.label }}
        </VTag>
        <LinkApplicationSourceBadge :application="application" />
      </div>

      <VAlert v-if="reviewMode === 'resume'" title="审批已保留" type="info" :closable="false">
        <template #description> 审批字段已冻结，无法修改、拒绝或删除。点击“继续审批”以完成创建正式链接。 </template>
      </VAlert>

      <LinkApplicationOriginDetails :application="application" />

      <!-- Editable approval form for pending applications -->
      <FormKit
        v-if="reviewMode === 'editable'"
        id="link-application-form"
        name="link-application-form"
        type="form"
        :config="{ validationVisibility: 'submit' }"
        :value="{
          url: application.spec.url,
          displayName: application.spec.displayName,
          logo: application.spec.logo || '',
          description: application.spec.description || '',
          groupName: '',
        }"
        @submit="handleApprove"
      >
        <FormKit type="text" name="displayName" validation="required" label="网站名称" />
        <FormKit type="url" name="url" validation="required" label="链接地址" />
        <FormKit type="url" name="logo" label="Logo" />
        <FormKit type="textarea" name="description" label="简介" auto-height />
        <FormKit type="select" name="groupName" label="分配分组" :options="groupOptions" />
      </FormKit>

      <!-- Frozen fields for approving / terminal applications -->
      <dl v-else class=":uno: text-sm space-y-3">
        <div>
          <dt class=":uno: text-gray-500">网站名称</dt>
          <dd class=":uno: mt-1 text-gray-700">{{ frozenFields.displayName }}</dd>
        </div>
        <div>
          <dt class=":uno: text-gray-500">链接地址</dt>
          <dd class=":uno: mt-1 text-gray-700">
            <a :href="frozenFields.url" target="_blank" class=":uno: text-blue-600 hover:underline">
              {{ frozenFields.url }}
            </a>
          </dd>
        </div>
        <div v-if="frozenFields.logo">
          <dt class=":uno: text-gray-500">Logo</dt>
          <dd class=":uno: mt-1 break-all text-gray-700">{{ frozenFields.logo }}</dd>
        </div>
        <div v-if="frozenFields.description">
          <dt class=":uno: text-gray-500">简介</dt>
          <dd class=":uno: mt-1 whitespace-pre-wrap text-gray-700">{{ frozenFields.description }}</dd>
        </div>
        <div>
          <dt class=":uno: text-gray-500">分配分组</dt>
          <dd class=":uno: mt-1 text-gray-700">{{ frozenGroupLabel }}</dd>
        </div>
        <div v-if="application.spec.approval?.linkName">
          <dt class=":uno: text-gray-500">正式链接</dt>
          <dd class=":uno: mt-1 text-gray-700">{{ application.spec.approval.linkName }}</dd>
        </div>
      </dl>

      <!-- Email (read-only display) -->
      <div v-if="application.spec.email">
        <label class=":uno: mb-1 block text-sm text-gray-700 font-medium">联系邮箱</label>
        <div class=":uno: text-sm text-gray-600">{{ application.spec.email }}</div>
      </div>

      <!-- Backlink -->
      <div v-if="application.spec.backlink">
        <label class=":uno: mb-1 block text-sm text-gray-700 font-medium">反链地址</label>
        <div class=":uno: flex items-center gap-2">
          <a
            :href="application.spec.backlink"
            target="_blank"
            class=":uno: flex-1 truncate text-sm text-blue-600 hover:underline"
          >
            {{ application.spec.backlink }}
          </a>
          <VButton v-if="backlinkVerificationAvailable" size="xs" type="secondary" @click="handleVerify">
            验证反链
          </VButton>
        </div>
        <div
          v-if="verifyResult"
          class=":uno: mt-1 text-xs"
          :class="verifyResult.found ? ':uno: text-green-600' : ':uno: text-red-600'"
        >
          {{ verifyResult.message }}
        </div>
      </div>

      <!-- Feed URLs -->
      <div v-if="application.spec.feedUrls?.length">
        <label class=":uno: mb-1 block text-sm text-gray-700 font-medium">RSS 地址</label>
        <div v-for="(feedUrl, index) in application.spec.feedUrls" :key="index" class=":uno: text-sm text-gray-600">
          {{ feedUrl }}
        </div>
      </div>
    </div>

    <template #footer>
      <VSpace v-if="reviewMode === 'editable'">
        <VButton :loading="isApproving" type="secondary" @click="$formkit.submit('link-application-form')">
          通过
        </VButton>
        <VButton :loading="isRejecting" type="danger" @click="handleReject"> 拒绝 </VButton>
        <VButton type="default" @click="handleDelete">删除</VButton>
        <VButton @click="modal?.close()">取消</VButton>
      </VSpace>
      <VSpace v-else-if="reviewMode === 'resume'">
        <VButton :loading="isApproving" type="secondary" @click="handleResumeApproval"> 继续审批 </VButton>
        <VButton @click="modal?.close()">取消</VButton>
      </VSpace>
      <VSpace v-else>
        <VButton type="danger" @click="handleDelete">删除</VButton>
        <VButton @click="modal?.close()">关闭</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
