<script lang="ts" setup>
import {
  useApproveLinkApplication,
  useDeleteLinkApplication,
  useRejectLinkApplication,
  useVerifyBacklink,
} from "@/composables/use-link-application";
import { useLinkGroupFetch } from "@/composables/use-group-fetch";
import { startInitialLinkFeedRefresh } from "@/composables/link-feed-initial-refresh";
import { startLinkVerification } from "@/composables/link-verification";
import { QK_GROUPS_WITH_LINKS, QK_RSS_GROUPS_WITH_LINKS } from "@/composables/use-link-fetch";
import { QK_LINK_GROUPS } from "@/composables/use-group-fetch";
import type { LinkApplication, ApproveRequest } from "@/api/generated";
import {
  Dialog,
  Toast,
  VButton,
  VModal,
  VSpace,
  VTag,
} from "@halo-dev/components";
import { useQueryClient } from "@tanstack/vue-query";
import { computed } from "vue";

const props = defineProps<{
  application: LinkApplication;
}>();

const emit = defineEmits<{
  (event: "close"): void;
}>();

const queryClient = useQueryClient();

const { data: groups } = useLinkGroupFetch();

const { mutate: approveApplication, isPending: isApproving } = useApproveLinkApplication();
const { mutate: rejectApplication, isPending: isRejecting } = useRejectLinkApplication();
const { mutate: deleteApplication } = useDeleteLinkApplication();
const { mutate: verifyApplication, data: verifyResult } = useVerifyBacklink();

const groupOptions = computed(() => [
  { value: "", label: "不分配" },
  ...(groups.value || []).map((group) => ({
    value: group.metadata.name,
    label: group.spec?.displayName || group.metadata.name,
  })),
]);

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
      request: {
        url: data.url.trim(),
        displayName: data.displayName.trim(),
        logo: data.logo || undefined,
        description: data.description || undefined,
        groupName: data.groupName || undefined,
      },
    },
    {
      onSuccess: (createdLink) => {
        Toast.success("已通过申请");
        queryClient.invalidateQueries({ queryKey: [QK_GROUPS_WITH_LINKS] });
        queryClient.invalidateQueries({ queryKey: [QK_RSS_GROUPS_WITH_LINKS] });
        queryClient.invalidateQueries({ queryKey: [QK_LINK_GROUPS] });
        // Trigger post-approval automation
        if (createdLink?.metadata?.name) {
          startLinkVerification({
            request: { names: [createdLink.metadata.name] },
            queryClient,
          });
          startInitialLinkFeedRefresh({
            linkName: createdLink.metadata.name,
            queryClient,
          });
        }
        emit("close");
      },
    }
  );
}

function handleReject() {
  Dialog.warning({
    title: "确认拒绝申请？",
    description: `拒绝 "${props.application.spec.displayName}" 的申请后，该链接将无法再次提交。`,
    confirmType: "danger",
    onConfirm: () => {
      rejectApplication(props.application.metadata.name, {
        onSuccess: () => {
          Toast.success("已拒绝申请");
          emit("close");
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
          emit("close");
        },
      });
    },
  });
}

const statusText: Record<string, string> = {
  PENDING: "待审核",
  APPROVED: "已通过",
  REJECTED: "已拒绝",
};

const statusType: Record<string, "default" | "primary" | "success" | "warning" | "danger"> = {
  PENDING: "warning",
  APPROVED: "success",
  REJECTED: "danger",
};
</script>

<template>
  <VModal
    :title="`审核申请 - ${application.spec.displayName}`"
    :width="600"
    :mount-to-body="true"
    @close="emit('close')"
  >
    <div class=":uno: space-y-4">
      <!-- Status -->
      <div class=":uno: flex items-center gap-2">
        <span class=":uno: text-sm text-gray-500">状态：</span>
        <VTag :type="statusType[application.spec.status]" size="sm">
          {{ statusText[application.spec.status] }}
        </VTag>
      </div>

      <!-- FormKit Form -->
      <FormKit
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
        <div class=":uno: space-y-3">
          <FormKit
            type="text"
            name="displayName"
            validation="required"
            label="网站名称"
          />
          <FormKit
            type="url"
            name="url"
            validation="required"
            label="链接地址"
          />
          <FormKit type="url" name="logo" label="Logo" />
          <FormKit
            type="textarea"
            name="description"
            label="简介"
            auto-height
          />
          <FormKit
            type="select"
            name="groupName"
            label="分配分组"
            :options="groupOptions"
          />
        </div>
      </FormKit>

      <!-- Email (read-only display) -->
      <div v-if="application.spec.email">
        <label class=":uno: block text-sm font-medium text-gray-700 mb-1">联系邮箱</label>
        <div class=":uno: text-sm text-gray-600">{{ application.spec.email }}</div>
      </div>

      <!-- Backlink -->
      <div v-if="application.spec.backlink">
        <label class=":uno: block text-sm font-medium text-gray-700 mb-1">反链地址</label>
        <div class=":uno: flex items-center gap-2">
          <a
            :href="application.spec.backlink"
            target="_blank"
            class=":uno: text-sm text-blue-600 hover:underline truncate flex-1"
          >
            {{ application.spec.backlink }}
          </a>
          <VButton size="xs" type="secondary" @click="handleVerify">
            验证反链
          </VButton>
        </div>
        <div v-if="verifyResult" class=":uno: mt-1 text-xs" :class="verifyResult.found ? 'text-green-600' : 'text-red-600'">
          {{ verifyResult.message }}
        </div>
      </div>

      <!-- Feed URLs -->
      <div v-if="application.spec.feedUrls?.length">
        <label class=":uno: block text-sm font-medium text-gray-700 mb-1">RSS 地址</label>
        <div
          v-for="(feedUrl, index) in application.spec.feedUrls"
          :key="index"
          class=":uno: text-sm text-gray-600"
        >
          {{ feedUrl }}
        </div>
      </div>
    </div>

    <template #footer>
      <VSpace>
        <VButton
          v-if="application.spec.status === 'PENDING'"
          :loading="isApproving"
          type="primary"
          @click="$formkit.submit('link-application-form')"
        >
          通过
        </VButton>
        <VButton
          v-if="application.spec.status === 'PENDING'"
          :loading="isRejecting"
          type="danger"
          @click="handleReject"
        >
          拒绝
        </VButton>
        <VButton type="default" @click="handleDelete">删除</VButton>
        <VButton @click="emit('close')">取消</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
