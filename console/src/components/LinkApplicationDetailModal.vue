<script lang="ts" setup>
import type { ApproveRequest, LinkApplication } from "@/api/generated";
import { QK_LINK_GROUPS, useLinkGroupFetch } from "@/composables/use-group-fetch";
import {
  useApproveLinkApplication,
  useDeleteLinkApplication,
  useLinkApplicationOriginComment,
  useRejectLinkApplication,
  useVerifyBacklink,
} from "@/composables/use-link-application";
import { QK_GROUPS_WITH_LINKS, QK_RSS_GROUPS_WITH_LINKS } from "@/composables/use-link-fetch";
import { approveLinkThenHandleOriginComment, useOriginCommentActions } from "@/composables/use-origin-comment-actions";
import {
  linkApplicationCommentActionsView,
  originCommentActionFailureAlert,
  originCommentActionSuccessMessage,
  originCommentOutcomeRequiresReplyConfirmation,
  type LinkApplicationOriginCommentState,
  type OriginCommentActionIntent,
  type OriginCommentActionOutcome,
} from "@/utils/link-application-comment-actions";
import {
  buildLinkApplicationApprovalRequest,
  linkApplicationEffectiveFields,
  linkApplicationOriginCommentErrorState,
  linkApplicationRejectDescription,
  linkApplicationReviewMode,
  linkApplicationStatusMeta,
  type LinkApplicationApprovalFormData,
} from "@/utils/link-application-review";
import { Dialog, Toast, VAlert, VButton, VLoading, VModal, VSpace, VTag } from "@halo-dev/components";
import { utils } from "@halo-dev/ui-shared";
import { useQueryClient } from "@tanstack/vue-query";
import { computed, reactive, shallowRef, useTemplateRef, watch } from "vue";
import MingcuteEarth3Line from "~icons/mingcute/earth-3-line";
import MingcuteMailLine from "~icons/mingcute/mail-line";
import LinkApplicationOriginCommentActions from "./LinkApplicationOriginCommentActions.vue";
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

const { mutateAsync: approveApplication, isPending: isApproving } = useApproveLinkApplication();
const { mutate: rejectApplication, isPending: isRejecting } = useRejectLinkApplication();
const { mutate: deleteApplication } = useDeleteLinkApplication();
const { mutate: verifyApplication, isPending: isVerifying } = useVerifyBacklink();

const reviewMode = computed(() => linkApplicationReviewMode(props.application));
const statusMeta = computed(() => linkApplicationStatusMeta(effectiveStatus.value));
const frozenFields = computed<ApproveRequest>(
  () => submittedApprovalRequest.value ?? linkApplicationEffectiveFields(props.application),
);
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

const {
  data: originComment,
  isLoading: isOriginCommentLoading,
  error: originCommentError,
  refetch: refetchOriginCommentQuery,
} = useLinkApplicationOriginComment(computed(() => props.application));

// Local approved phase retained when link approval succeeded but Comment handling did not,
// so the modal stays open in approved mode without mutating the application prop.
const linkApproved = shallowRef(false);
// The request submitted from the editable form, so frozen fields reflect what was actually
// approved instead of pre-review submission data.
const submittedApprovalRequest = shallowRef<ApproveRequest>();
const commentOutcome = shallowRef<Exclude<OriginCommentActionOutcome, { type: "completed" }>>();
const awaitingReplyConfirmation = shallowRef(false);
// Set when Halo rejects a Comment mutation with 401/403 while the modal is open, so
// permission-dependent controls stop allowing repeated submissions.
const commentPermissionLost = shallowRef(false);

const approveOverride = shallowRef<boolean>();
const replyText = shallowRef("");

watch(
  () => props.application.metadata.name,
  () => {
    linkApproved.value = false;
    submittedApprovalRequest.value = undefined;
    commentOutcome.value = undefined;
    awaitingReplyConfirmation.value = false;
    commentPermissionLost.value = false;
    approveOverride.value = undefined;
    replyText.value = "";
  },
);

const effectiveStatus = computed(() => (linkApproved.value ? "APPROVED" : props.application.spec.status));
const effectiveReviewMode = computed(() => (linkApproved.value ? "readonly" : reviewMode.value));

const modalTitle = computed(() =>
  effectiveReviewMode.value === "editable"
    ? `审核申请 - ${props.application.spec.displayName}`
    : `申请详情 - ${props.application.spec.displayName}`,
);

// permission.has defaults to "any", so pass false to require both link and comment management.
const canManageComments = computed(
  () => !commentPermissionLost.value && utils.permission.has(["plugin:links:manage", "system:comments:manage"], false),
);

const originCommentState = computed<LinkApplicationOriginCommentState>(() => {
  if (isOriginCommentLoading.value) {
    return "loading";
  }
  if (originCommentError.value) {
    return linkApplicationOriginCommentErrorState(originCommentError.value) === "unavailable" ? "deleted" : "error";
  }
  return originComment.value ? "ready" : "deleted";
});

const commentActionsView = computed(() =>
  linkApplicationCommentActionsView({
    originType: props.application.spec.origin.type,
    applicationStatus: effectiveStatus.value,
    commentState: originCommentState.value,
    approved: originComment.value?.approved,
    hidden: originComment.value?.hidden,
  }),
);

const approveCommentSelected = computed({
  get: () => approveOverride.value ?? commentActionsView.value.approvalDefaultSelected,
  set: (value: boolean) => {
    approveOverride.value = value;
  },
});

const commentIntent = computed<OriginCommentActionIntent>(() => {
  if (!commentActionsView.value.controlsEnabled || !canManageComments.value) {
    return { approve: false, replyText: "" };
  }
  return {
    approve: commentActionsView.value.approvalOffered && approveCommentSelected.value,
    replyText: replyText.value,
  };
});

// While the source Comment state is loading, the intent would degrade to a no-op and bypass the
// default approval selection, so block link approval until the state is known.
const linkApprovalDisabled = computed(() => commentActionsView.value.visible && originCommentState.value === "loading");

const commentFailureAlert = computed(() =>
  commentOutcome.value
    ? originCommentActionFailureAlert({
        outcome: commentOutcome.value,
        includesLinkApproval: linkApproved.value,
      })
    : undefined,
);

const { isProcessing: isHandlingComment, handleOriginComment } = useOriginCommentActions({
  application: computed(() => props.application),
  refetchOriginComment: async () => (await refetchOriginCommentQuery()).data,
});

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

function invalidateLinkQueries() {
  queryClient.invalidateQueries({ queryKey: [QK_GROUPS_WITH_LINKS] });
  queryClient.invalidateQueries({ queryKey: [QK_RSS_GROUPS_WITH_LINKS] });
  queryClient.invalidateQueries({ queryKey: [QK_LINK_GROUPS] });
}

function handleCommentOutcome(outcome: OriginCommentActionOutcome) {
  if (outcome.type === "completed") {
    Toast.success(originCommentActionSuccessMessage(outcome, linkApproved.value));
    invalidateLinkQueries();
    modal.value?.close();
    return;
  }
  commentOutcome.value = outcome;
  if (outcome.type === "failed" && (outcome.status === 401 || outcome.status === 403)) {
    commentPermissionLost.value = true;
  }
  if (originCommentOutcomeRequiresReplyConfirmation(outcome)) {
    awaitingReplyConfirmation.value = true;
  }
  if (linkApproved.value) {
    invalidateLinkQueries();
  }
}

async function approveAndHandleComment(approveLink: () => Promise<unknown>, approvedRequest?: ApproveRequest) {
  commentOutcome.value = undefined;
  const result = await approveLinkThenHandleOriginComment({
    approveLink,
    handleComment: () => handleOriginComment(commentIntent.value),
  });
  if (!result.linkApproved) {
    // The global interceptor reports the approval failure; no Comment request was sent.
    return;
  }
  linkApproved.value = true;
  submittedApprovalRequest.value = approvedRequest;
  handleCommentOutcome(result.commentOutcome ?? { type: "completed", approvedComment: false, replied: false });
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
  const request = buildLinkApplicationApprovalRequest(data);
  void approveAndHandleComment(() => approveApplication({ name: props.application.metadata.name, request }), request);
}

function handleResumeApproval() {
  void approveAndHandleComment(() => approveApplication({ name: props.application.metadata.name }));
}

function handleCommentActionSubmit(intent: OriginCommentActionIntent) {
  if (awaitingReplyConfirmation.value && intent.replyText.trim()) {
    Dialog.warning({
      title: "确认重新提交回复？",
      description: "上次回复请求结果未知，重复提交可能产生重复回复，请确认已核对评论与回复状态。",
      confirmType: "danger",
      onConfirm: () => {
        awaitingReplyConfirmation.value = false;
        void runStandaloneCommentActions(intent);
      },
    });
    return;
  }
  void runStandaloneCommentActions(intent);
}

async function runStandaloneCommentActions(intent: OriginCommentActionIntent) {
  commentOutcome.value = undefined;
  const outcome = await handleOriginComment(intent);
  handleCommentOutcome(outcome);
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

      <VAlert v-if="effectiveReviewMode === 'resume'" title="审批已保留" type="info" :closable="false">
        <template #description> 审批字段已冻结，无法修改、拒绝或删除。点击“继续审批”以完成创建正式链接。 </template>
      </VAlert>

      <LinkApplicationOriginDetails :application="application" />

      <VAlert v-if="commentFailureAlert" :title="commentFailureAlert.title" type="error" :closable="false">
        <template #description>{{ commentFailureAlert.description }}</template>
      </VAlert>

      <LinkApplicationOriginCommentActions
        v-model:approve="approveCommentSelected"
        v-model:reply-text="replyText"
        :view="commentActionsView"
        :can-manage-comments="canManageComments"
        :processing="isHandlingComment"
        @submit="handleCommentActionSubmit"
      />

      <!-- Editable approval form for pending applications -->
      <FormKit
        v-if="effectiveReviewMode === 'editable'"
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
      <VSpace v-if="effectiveReviewMode === 'editable'">
        <VButton
          :loading="isApproving || isHandlingComment"
          :disabled="linkApprovalDisabled"
          type="secondary"
          @click="$formkit.submit('link-application-form')"
        >
          通过
        </VButton>
        <VButton :loading="isRejecting" type="danger" @click="handleReject"> 拒绝 </VButton>
        <VButton type="default" @click="handleDelete">删除</VButton>
        <VButton @click="modal?.close()">取消</VButton>
      </VSpace>
      <VSpace v-else-if="effectiveReviewMode === 'resume'">
        <VButton
          :loading="isApproving || isHandlingComment"
          :disabled="linkApprovalDisabled"
          type="secondary"
          @click="handleResumeApproval"
        >
          继续审批
        </VButton>
        <VButton @click="modal?.close()">取消</VButton>
      </VSpace>
      <VSpace v-else>
        <VButton type="danger" @click="handleDelete">删除</VButton>
        <VButton @click="modal?.close()">关闭</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
