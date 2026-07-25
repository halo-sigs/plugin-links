<script lang="ts" setup>
import type { LinkApplication } from "@/api/generated";
import { linkApplicationCommentRoute, linkApplicationSubjectMeta } from "@/utils/link-application-origin";
import { coreApiClient } from "@halo-dev/api-client";
import { useQuery } from "@tanstack/vue-query";
import { computed } from "vue";

const props = defineProps<{
  application: LinkApplication;
}>();

const origin = computed(() => props.application.spec.origin);
const commentName = computed(() => origin.value?.comment?.name);
const commentQueryEnabled = computed(() => origin.value?.type === "COMMENT" && !!commentName.value);
const { data: sourceComment, isFetching: isCommentLoading } = useQuery({
  queryKey: ["plugin:links:link-application-origin-comment", commentName],
  enabled: commentQueryEnabled,
  queryFn: async () => {
    const { data } = await coreApiClient.content.comment.listComment({
      fieldSelector: [`metadata.name==${commentName.value}`],
      page: 1,
      size: 1,
    });
    return data.items[0] ?? null;
  },
  retry: false,
});
const subject = computed(() => linkApplicationSubjectMeta(sourceComment.value?.spec.subjectRef));
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
        <dd v-if="isCommentLoading" class=":uno: mt-1 text-gray-500">正在加载评论...</dd>
        <dd v-else-if="sourceComment" class=":uno: mt-1">
          <RouterLink v-if="commentRoute" :to="commentRoute" class=":uno: text-blue-600 hover:underline">
            在评论管理中查找
          </RouterLink>
          <span class=":uno: ml-2 break-all text-xs text-gray-400">{{ commentName }}</span>
        </dd>
        <dd v-else class=":uno: mt-1 text-gray-500">原评论不可用</dd>
      </div>

      <div v-if="sourceComment">
        <dt class=":uno: text-gray-500">当前评论内容</dt>
        <dd class=":uno: mt-1">
          <pre
            class=":uno: max-h-60 overflow-auto whitespace-pre-wrap break-words border border-gray-200 rounded bg-white p-3 text-xs text-gray-700"
            >{{ sourceComment.spec.raw }}</pre
          >
        </dd>
      </div>
    </dl>
  </section>
</template>
