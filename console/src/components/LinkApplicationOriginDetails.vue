<script lang="ts" setup>
import type { LinkApplication } from "@/api/generated";
import { linkApplicationCommentRoute, linkApplicationSubjectMeta } from "@/utils/link-application-origin";
import { computed } from "vue";

const props = defineProps<{
  application: LinkApplication;
}>();

const origin = computed(() => props.application.spec.origin);
const subject = computed(() => linkApplicationSubjectMeta(origin.value?.subjectRef));
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

      <div v-if="origin.commentName">
        <dt class=":uno: text-gray-500">原评论</dt>
        <dd class=":uno: mt-1">
          <RouterLink v-if="commentRoute" :to="commentRoute" class=":uno: text-blue-600 hover:underline">
            在评论管理中查找
          </RouterLink>
          <span v-else class=":uno: text-gray-500">原评论不可用</span>
          <span class=":uno: ml-2 break-all text-xs text-gray-400">{{ origin.commentName }}</span>
        </dd>
      </div>

      <div v-if="origin.modelName">
        <dt class=":uno: text-gray-500">识别模型</dt>
        <dd class=":uno: mt-1 break-all text-gray-700">{{ origin.modelName }}</dd>
      </div>

      <div v-if="origin.reason">
        <dt class=":uno: text-gray-500">识别原因</dt>
        <dd class=":uno: mt-1 whitespace-pre-wrap text-gray-700">{{ origin.reason }}</dd>
      </div>

      <div>
        <dt class=":uno: text-gray-500">评论快照</dt>
        <dd v-if="origin.commentSnapshot" class=":uno: mt-1">
          <pre
            class=":uno: max-h-60 overflow-auto whitespace-pre-wrap break-words border border-gray-200 rounded bg-white p-3 text-xs text-gray-700"
            >{{ origin.commentSnapshot }}</pre
          >
        </dd>
        <dd v-else class=":uno: mt-1 text-gray-500">未保留评论快照</dd>
        <p class=":uno: mt-1 text-xs text-gray-400">原评论已删除或无法访问时，仍可依据此处保留的快照审核。</p>
      </div>
    </dl>
  </section>
</template>
