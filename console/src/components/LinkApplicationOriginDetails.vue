<script lang="ts" setup>
import type { LinkApplication } from "@/api/generated";
import { useLinkApplicationOriginComment } from "@/composables/use-link-application";
import { linkApplicationCommentRoute, linkApplicationSubjectMeta } from "@/utils/link-application-origin";
import { linkApplicationOriginCommentErrorState } from "@/utils/link-application-review";
import { VDescription, VDescriptionItem } from "@halo-dev/components";
import { utils } from "@halo-dev/ui-shared";
import { computed } from "vue";

const props = defineProps<{
  application: LinkApplication;
}>();

const origin = computed(() => props.application.spec.origin);
const commentName = computed(() => origin.value.comment?.name);

const { data: originComment, isLoading, error } = useLinkApplicationOriginComment(computed(() => props.application));

const errorState = computed(() => (error.value ? linkApplicationOriginCommentErrorState(error.value) : undefined));

const subject = computed(() => linkApplicationSubjectMeta(originComment.value?.subjectRef));
const commentRoute = computed(() => linkApplicationCommentRoute(props.application));
</script>

<template>
  <section v-if="origin.type === 'COMMENT'" class=":uno: border border-gray-200 rounded-lg bg-gray-50 p-3">
    <h3 class=":uno: mb-3 text-sm text-gray-900 font-medium">评论识别信息</h3>

    <div class=":uno: overflow-hidden border border-gray-100 rounded">
      <VDescription>
        <VDescriptionItem v-if="subject" label="评论来源">
          <RouterLink v-if="subject.route" :to="subject.route" class=":uno: text-blue-600 hover:underline">
            {{ subject.label }}
          </RouterLink>
          <span v-else>{{ subject.label }}</span>
        </VDescriptionItem>

        <VDescriptionItem label="原评论">
          <span v-if="isLoading" class=":uno: text-gray-500">正在加载评论...</span>
          <template v-else-if="originComment">
            <RouterLink v-if="commentRoute" :to="commentRoute" class=":uno: text-blue-600 hover:underline">
              打开评论管理
            </RouterLink>
            <span class=":uno: ml-2 break-all text-xs text-gray-400">{{ commentName }}</span>
          </template>
          <span v-else-if="errorState === 'forbidden'" class=":uno: text-gray-500">
            当前账号没有查看来源评论的权限
          </span>
          <span v-else-if="errorState === 'error'" class=":uno: text-gray-500">来源评论加载失败，请稍后重试</span>
          <span v-else class=":uno: text-gray-500">原评论不可用（可能已被删除）</span>
        </VDescriptionItem>

        <VDescriptionItem v-if="originComment?.raw" label="当前评论内容">
          <pre
            class=":uno: max-h-60 overflow-auto whitespace-pre-wrap break-words border border-gray-200 rounded bg-white p-3 text-xs text-gray-700"
            >{{ originComment.raw }}</pre
          >
        </VDescriptionItem>

        <VDescriptionItem
          v-if="originComment?.creationTime"
          label="评论时间"
          :content="utils.date.format(originComment.creationTime)"
        />
      </VDescription>
    </div>
  </section>
</template>
