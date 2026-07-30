<script lang="ts" setup>
import type { LinkApplication } from "@/api/generated";
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
  type LinkApplicationApprovalFormData,
  linkApplicationEffectiveFields,
  linkApplicationRejectDescription,
  linkApplicationReviewMode,
  linkApplicationStatusMeta,
} from "@/utils/link-application-review";
import { Dialog, Toast, VAlert, VButton, VLoading, VModal, VSpace, VTag } from "@halo-dev/components";
import { useQueryClient } from "@tanstack/vue-query";
import { computed, reactive, useTemplateRef } from "vue";
import MingcuteEarth3Line from "~icons/mingcute/earth-3-line";
import MingcuteMailLine from "~icons/mingcute/mail-line";
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
const { mutate: verifyApplication, isPending: isVerifying } = useVerifyBacklink();

const reviewMode = computed(() => linkApplicationReviewMode(props.application));
const statusMeta = computed(() => linkApplicationStatusMeta(props.application.spec.status));
const frozenFields = computed(() => linkApplicationEffectiveFields(props.application));
const approvalForm = reactive<LinkApplicationApprovalFormData>({
  url: props.application.spec.url,
  displayName: props.application.spec.displayName,
  logo: props.application.spec.logo || "",
  description: props.application.spec.description || "",
  groupName: "",
  backlink: props.application.spec.backlink || "",
  feedUrlsText: props.application.spec.feedUrls?.join("\n") || "",
});
const verificationBacklink = computed(() => approvalForm.backlink?.trim());

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
  return group?.spec?.displayName || "未知分组";
});

const groupOptions = computed(() => [
  { value: "", label: "不分配" },
  ...(groups.value || []).flatMap((group) => {
    const name = group.metadata?.name;
    const displayName = group.spec?.displayName;
    if (!name || !displayName) {
      return [];
    }
    return {
      value: name,
      label: displayName,
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

function handleApprove(data: LinkApplicationApprovalFormData) {
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
  verifyApplication(
    {
      name,
      backlink: verificationBacklink.value,
    },
    {
      onSuccess: (result) => {
        if (result.found) {
          Toast.success(result.message || "反链验证通过");
        } else {
          Toast.warning(result.message || "反链验证未通过");
        }
      },
    },
  );
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
      <div
        class=":uno: flex flex-wrap items-center gap-x-4 gap-y-2 border border-gray-200 rounded-lg bg-gray-50 px-3.5 py-2.5"
      >
        <VTag :type="statusMeta.tagType" size="sm">
          {{ statusMeta.label }}
        </VTag>
        <LinkApplicationSourceBadge :application="application" />
        <span v-if="application.spec.email" class=":uno: inline-flex items-center gap-1.5 text-sm text-gray-600">
          <MingcuteMailLine class=":uno: size-3.5 text-gray-400" />
          {{ application.spec.email }}
        </span>
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
        @submit="handleApprove"
      >
        <FormKit
          v-model="approvalForm.displayName"
          type="text"
          name="displayName"
          validation="required"
          label="网站名称"
        />
        <FormKit v-model="approvalForm.url" type="url" name="url" validation="required" label="链接地址" />
        <FormKit v-model="approvalForm.logo" type="url" name="logo" label="Logo" />
        <FormKit v-model="approvalForm.description" type="textarea" name="description" label="简介" auto-height />
        <FormKit
          v-model="approvalForm.groupName"
          type="select"
          name="groupName"
          label="分配分组"
          :options="groupOptions"
        />
        <FormKit
          v-model="approvalForm.backlink"
          type="url"
          name="backlink"
          label="反链地址"
          help="填写对方固定放置本站链接的页面，留空则不检测反链"
          placeholder="https://example.com/links"
        >
          <template v-if="verificationBacklink" #suffix>
            <button
              v-tooltip="{
                content: '验证反链',
              }"
              type="button"
              aria-label="验证反链"
              class=":uno: group h-full flex cursor-pointer items-center border-0 border-l border-gray-200 bg-transparent px-3 transition-all disabled:cursor-not-allowed hover:bg-gray-100 disabled:opacity-50"
              :disabled="isVerifying"
              @click="handleVerify"
            >
              <VLoading v-if="isVerifying" class=":uno: size-4 text-gray-500" />
              <MingcuteEarth3Line v-else class=":uno: size-4 text-gray-500 group-hover:text-gray-700" />
            </button>
          </template>
        </FormKit>
        <FormKit
          v-model="approvalForm.feedUrlsText"
          type="textarea"
          name="feedUrlsText"
          label="RSS 地址"
          help="每行一个 RSS 或 Atom 地址"
          placeholder="https://example.com/rss.xml&#10;https://example.com/atom.xml"
          auto-height
        />
      </FormKit>

      <!-- Frozen fields for approving / terminal applications -->
      <dl v-else class=":uno: overflow-hidden border border-gray-200 rounded-lg divide-y divide-gray-100">
        <div class=":uno: flex gap-3 px-3.5 py-2.5 text-sm">
          <dt class=":uno: w-18 shrink-0 text-gray-500">网站名称</dt>
          <dd class=":uno: min-w-0 flex-1 break-all text-gray-900">{{ frozenFields.displayName }}</dd>
        </div>
        <div class=":uno: flex gap-3 px-3.5 py-2.5 text-sm">
          <dt class=":uno: w-18 shrink-0 text-gray-500">链接地址</dt>
          <dd class=":uno: min-w-0 flex-1">
            <a :href="frozenFields.url" target="_blank" class=":uno: break-all text-blue-600 hover:underline">
              {{ frozenFields.url }}
            </a>
          </dd>
        </div>
        <div v-if="frozenFields.logo" class=":uno: flex gap-3 px-3.5 py-2.5 text-sm">
          <dt class=":uno: w-18 shrink-0 text-gray-500">Logo</dt>
          <dd class=":uno: min-w-0 flex-1 break-all text-gray-900">{{ frozenFields.logo }}</dd>
        </div>
        <div v-if="frozenFields.description" class=":uno: flex gap-3 px-3.5 py-2.5 text-sm">
          <dt class=":uno: w-18 shrink-0 text-gray-500">简介</dt>
          <dd class=":uno: min-w-0 flex-1 whitespace-pre-wrap text-gray-900">{{ frozenFields.description }}</dd>
        </div>
        <div class=":uno: flex gap-3 px-3.5 py-2.5 text-sm">
          <dt class=":uno: w-18 shrink-0 text-gray-500">分配分组</dt>
          <dd class=":uno: min-w-0 flex-1 text-gray-900">{{ frozenGroupLabel }}</dd>
        </div>
        <div v-if="frozenFields.backlink" class=":uno: flex gap-3 px-3.5 py-2.5 text-sm">
          <dt class=":uno: w-18 shrink-0 text-gray-500">反链地址</dt>
          <dd class=":uno: min-w-0 flex-1">
            <a :href="frozenFields.backlink" target="_blank" class=":uno: break-all text-blue-600 hover:underline">
              {{ frozenFields.backlink }}
            </a>
          </dd>
        </div>
        <div v-if="frozenFields.feedUrls?.length" class=":uno: flex gap-3 px-3.5 py-2.5 text-sm">
          <dt class=":uno: w-18 shrink-0 text-gray-500">RSS 地址</dt>
          <dd class=":uno: min-w-0 flex-1 text-gray-900 space-y-1">
            <div v-for="feedUrl in frozenFields.feedUrls" :key="feedUrl" class=":uno: break-all">
              {{ feedUrl }}
            </div>
          </dd>
        </div>
      </dl>
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
