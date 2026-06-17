<script lang="ts" setup>
import { linkAiApiClient } from "@/api";
import type { LinkCommentExtractionResult, LinkCommentSummaryDTO } from "@/api/generated";
import { commentPlainText } from "@/utils/comment-content";
import { IconClose, Toast, VButton, VLoading } from "@halo-dev/components";
import { utils } from "@halo-dev/ui-shared";
import { computed, ref, shallowRef } from "vue";
import MdiCommentTextOutline from "~icons/mdi/comment-text-outline";
import MdiRobot from "~icons/mdi/robot";

const emit = defineEmits<{
  (event: "extract", result: LinkCommentExtractionResult): void;
}>();

const showPanel = shallowRef(false);
const isLoadingComments = shallowRef(false);
const isExtracting = shallowRef(false);
const recentComments = ref<LinkCommentSummaryDTO[]>([]);
const selectedCommentName = shallowRef<string | undefined>(undefined);
const manualCommentText = shallowRef("");

const displayComments = computed(() =>
  recentComments.value.map((comment) => ({
    ...comment,
    plainContent: commentPlainText(comment),
  })),
);

async function handleFetchComments() {
  if (isLoadingComments.value) return;
  isLoadingComments.value = true;
  try {
    const { data } = await linkAiApiClient.ai.listRecentLinkComments();
    recentComments.value = data;
    if (!data.length) {
      Toast.info("暂无已审核的评论");
    }
  } catch {
    // Halo's API interceptor shows request failure toasts.
  } finally {
    isLoadingComments.value = false;
  }
}

function selectComment(comment: LinkCommentSummaryDTO) {
  selectedCommentName.value = comment.name;
  manualCommentText.value = commentPlainText(comment);
}

async function handleAiExtract() {
  const content = manualCommentText.value.trim();
  if (!content) {
    Toast.error("请选择一条评论或输入评论内容");
    return;
  }
  if (isExtracting.value) return;
  isExtracting.value = true;
  try {
    const { data: result } = await linkAiApiClient.ai.extractLinkFromComment({
      linkCommentExtractRequest: { content },
    });
    emit("extract", result);
    Toast.success("AI 识别成功");
    showPanel.value = false;
    reset();
  } finally {
    isExtracting.value = false;
  }
}

function reset() {
  selectedCommentName.value = undefined;
  manualCommentText.value = "";
  recentComments.value = [];
}

function open() {
  showPanel.value = true;
}
</script>

<template>
  <div>
    <div v-if="!showPanel" class=":uno: flex items-center gap-2">
      <VButton size="sm" type="secondary" @click="open">
        <template #icon>
          <MdiRobot class=":uno: size-4" />
        </template>
        从评论识别
      </VButton>
      <span class=":uno: text-xs text-gray-500">通过 AI 从访客评论中提取友链信息</span>
    </div>
    <div v-else class=":uno: border border-gray-200 rounded-xl bg-white p-4 shadow-sm space-y-4">
      <div class=":uno: flex items-center justify-between">
        <div class=":uno: flex items-center gap-2">
          <MdiRobot class=":uno: size-5 text-blue-600" />
          <span class=":uno: text-sm text-gray-900 font-semibold">从评论识别友链</span>
        </div>
        <button
          type="button"
          class=":uno: flex items-center justify-center rounded p-1 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600"
          @click="showPanel = false"
        >
          <IconClose class=":uno: size-4" />
        </button>
      </div>

      <p class=":uno: text-xs text-gray-500">从已审核的评论中提取网站地址、名称、Logo、描述和 RSS 订阅地址。</p>

      <VButton size="sm" :loading="isLoadingComments" @click="handleFetchComments">
        <template #icon>
          <MdiCommentTextOutline class=":uno: size-4" />
        </template>
        获取最新评论
      </VButton>

      <div v-if="isLoadingComments" class=":uno: flex justify-center py-6">
        <VLoading class=":uno: size-5 text-gray-400" />
      </div>

      <div
        v-else-if="recentComments.length"
        class=":uno: max-h-52 overflow-y-auto border border-gray-200 rounded-lg divide-y divide-gray-100"
      >
        <div
          v-for="comment in displayComments"
          :key="comment.name"
          class=":uno: cursor-pointer px-3 py-2.5 transition-colors"
          :class="selectedCommentName === comment.name ? ':uno: bg-blue-50/60' : ':uno: hover:bg-gray-50'"
          @click="selectComment(comment)"
        >
          <div class=":uno: flex items-center justify-between gap-2">
            <div class=":uno: min-w-0 flex items-center gap-2">
              <span
                class=":uno: inline-block size-2 flex-none rounded-full"
                :class="selectedCommentName === comment.name ? ':uno: bg-blue-500' : ':uno: bg-gray-300'"
              ></span>
              <span class=":uno: truncate text-sm text-gray-900 font-medium">
                {{ comment.ownerName || "匿名" }}
              </span>
            </div>
            <span class=":uno: flex-none text-xs text-gray-400">
              {{ utils.date.timeAgo(comment.creationTime) }}
            </span>
          </div>
          <p class=":uno: line-clamp-2 mt-1 pl-4 text-xs text-gray-500">
            {{ comment.plainContent }}
          </p>
        </div>
      </div>

      <div v-else class=":uno: border border-gray-200 rounded-lg border-dashed py-5 text-center">
        <p class=":uno: text-sm text-gray-400">暂无已审核的评论</p>
        <p class=":uno: mt-1 text-xs text-gray-400">可手动在下方粘贴评论内容</p>
      </div>

      <FormKit
        v-model="manualCommentText"
        type="textarea"
        name="commentContent"
        label="评论内容"
        help="可手动粘贴或编辑"
        auto-height
        placeholder="选择上方评论或手动粘贴内容，AI 将从中提取友链信息..."
      ></FormKit>

      <div class=":uno: flex justify-end">
        <VButton size="sm" type="secondary" :loading="isExtracting" @click="handleAiExtract">
          <template #icon>
            <MdiRobot class=":uno: size-4" />
          </template>
          AI 识别
        </VButton>
      </div>
    </div>
  </div>
</template>
