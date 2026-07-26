<script lang="ts" setup>
import type { LinkApplication } from "@/api/generated";
import { useLinkApplicationOriginComment } from "@/composables/use-link-application";
import { linkApplicationCommentRoute, linkApplicationSubjectMeta } from "@/utils/link-application-origin";
import { linkApplicationOriginCommentErrorState } from "@/utils/link-application-review";
import { utils } from "@halo-dev/ui-shared";
import { computed } from "vue";

const props = defineProps<{
  application: LinkApplication;
}>();

const origin = computed(() => props.application.spec.origin);
const commentName = computed(() => origin.value?.comment?.name);

const { data: originComment, isLoading, error } = useLinkApplicationOriginComment(computed(() => props.application));

const errorState = computed(() => (error.value ? linkApplicationOriginCommentErrorState(error.value) : undefined));

const subject = computed(() => linkApplicationSubjectMeta(originComment.value?.subjectRef));
const commentRoute = computed(() => linkApplicationCommentRoute(props.application));
</script>

<template>
  <section v-if="origin?.type === 'COMMENT'" class=":uno: border border-gray-200 rounded-lg bg-gray-50 p-3">
    <h3 class=":uno: mb-3 text-sm text-gray-900 font-medium">评论识别信息</h3>

    <dl class=":uno: text-sm space-y-3">
      <div v-if="subject">
        <dt class=":uno: text-gray-500">评论来源</dt>
        <dd class=":uno: mt-1 text-gray-700">
          <RouterLink v-if="subject.route" :to="subject.route" class=":uno: text-blue-600 hover:underline">
            {{ subject.label }}
          </RouterLink>
          <span v-else>{{ subject.label }}</span>
        </dd>
      </div>

      <div>
        <dt class=":uno: text-gray-500">原评论</dt>
        <dd v-if="isLoading" class=":uno: mt-1 text-gray-500">正在加载评论...</dd>
        <dd v-else-if="originComment" class=":uno: mt-1">
          <RouterLink v-if="commentRoute" :to="commentRoute" class=":uno: text-blue-600 hover:underline">
            打开评论管理
          </RouterLink>
          <span class=":uno: ml-2 break-all text-xs text-gray-400">{{ commentName }}</span>
        </dd>
        <dd v-else-if="errorState === 'forbidden'" class=":uno: mt-1 text-gray-500">当前账号没有查看来源评论的权限</dd>
        <dd v-else-if="errorState === 'error'" class=":uno: mt-1 text-gray-500">来源评论加载失败，请稍后重试</dd>
        <dd v-else class=":uno: mt-1 text-gray-500">原评论不可用（可能已被删除）</dd>
      </div>

      <div v-if="originComment?.raw">
        <dt class=":uno: text-gray-500">当前评论内容</dt>
        <dd class=":uno: mt-1">
          <pre
            class=":uno: max-h-60 overflow-auto whitespace-pre-wrap break-words border border-gray-200 rounded bg-white p-3 text-xs text-gray-700"
            >{{ originComment.raw }}</pre
          >
        </dd>
      </div>

      <div v-if="originComment?.creationTime">
        <dt class=":uno: text-gray-500">评论时间</dt>
        <dd class=":uno: mt-1 text-gray-700">{{ utils.date.format(originComment.creationTime) }}</dd>
      </div>
    </dl>
  </section>
</template>
