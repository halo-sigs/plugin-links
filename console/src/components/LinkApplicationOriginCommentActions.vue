<script lang="ts" setup>
import {
  linkApplicationActionSummary,
  originCommentActionIntentEmpty,
  type LinkApplicationCommentActionsView,
  type OriginCommentActionIntent,
} from "@/utils/link-application-comment-actions";
import { VAlert, VButton } from "@halo-dev/components";
import { computed } from "vue";

const props = defineProps<{
  view: LinkApplicationCommentActionsView;
  canManageComments: boolean;
  processing?: boolean;
}>();

const approve = defineModel<boolean>("approve", { default: false });
const replyText = defineModel<string>("replyText", { default: "" });

const emit = defineEmits<{
  (event: "submit", intent: OriginCommentActionIntent): void;
}>();

const controlsDisabled = computed(() => !props.view.controlsEnabled || !props.canManageComments || props.processing);

const hasReply = computed(() => !!replyText.value.trim());

const intent = computed<OriginCommentActionIntent>(() => {
  // Mirror the orchestrator's execution gate so the summary never promises actions that will
  // not run (missing permission or an unavailable source degrades every action to a no-op).
  if (!props.view.controlsEnabled || !props.canManageComments) {
    return { approve: false, replyText: "" };
  }
  return {
    approve: props.view.approvalOffered && approve.value,
    replyText: replyText.value,
  };
});

const summary = computed(() =>
  linkApplicationActionSummary({
    includesLinkApproval: !props.view.standalone,
    intent: intent.value,
  }),
);

const submitDisabled = computed(() => controlsDisabled.value || originCommentActionIntentEmpty(intent.value));

function handleSubmit() {
  if (submitDisabled.value) {
    return;
  }
  emit("submit", intent.value);
}
</script>

<template>
  <section v-if="view.visible" class=":uno: border border-gray-200 rounded-lg p-3.5">
    <h3 class=":uno: mb-3.5 text-sm text-gray-900 font-medium">来源评论处理</h3>

    <p v-if="!canManageComments" class=":uno: mb-3.5 text-xs text-gray-500">
      当前账号缺少友链管理（plugin:links:manage）或评论管理（system:comments:manage）权限，无法通过或回复来源评论。
    </p>

    <p v-else-if="!view.controlsEnabled" class=":uno: mb-3.5 text-xs text-gray-500">
      来源评论不可用（可能已被删除或加载失败），无法执行评论操作，但不影响链接审核。
    </p>

    <div v-if="view.hiddenWarning" class=":uno: mb-3.5">
      <VAlert title="评论处于隐藏状态" type="warning" :closable="false">
        <template #description>通过或回复评论不会改变其隐藏状态。</template>
      </VAlert>
    </div>

    <FormKit
      v-if="view.approvalOffered"
      v-model="approve"
      type="checkbox"
      name="approveOriginComment"
      label="同时通过来源评论"
      :help="hasReply ? '回复后评论将自动通过' : undefined"
      :disabled="controlsDisabled || hasReply"
    />

    <FormKit
      v-if="view.replyOffered"
      v-model="replyText"
      type="textarea"
      name="originCommentReply"
      label="回复来源评论（可选）"
      placeholder="回复后评论将自动通过"
      auto-height
      :disabled="controlsDisabled"
    />

    <p v-if="view.controlsEnabled" class=":uno: text-xs text-gray-500">{{ summary }}</p>

    <div v-if="view.standalone">
      <VButton size="sm" type="secondary" :loading="processing" :disabled="submitDisabled" @click="handleSubmit">
        处理评论
      </VButton>
    </div>
  </section>
</template>
