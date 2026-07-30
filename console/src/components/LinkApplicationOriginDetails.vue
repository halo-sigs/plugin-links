<script lang="ts" setup>
import type { LinkApplication } from "@/api/generated";
import { useLinkApplicationOriginComment } from "@/composables/use-link-application";
import { htmlToPlainText } from "@/utils/comment-content";
import { linkApplicationCommentRoute, linkApplicationSubjectMeta } from "@/utils/link-application-origin";
import { linkApplicationOriginCommentErrorState } from "@/utils/link-application-review";
import { IconExternalLinkLine, VDescription, VDescriptionItem } from "@halo-dev/components";
import { utils } from "@halo-dev/ui-shared";
import { computed } from "vue";

const props = defineProps<{
  application: LinkApplication;
}>();

const origin = computed(() => props.application.spec.origin);

const { data: originComment, isLoading, error } = useLinkApplicationOriginComment(computed(() => props.application));

const errorState = computed(() => (error.value ? linkApplicationOriginCommentErrorState(error.value) : undefined));

const subject = computed(() => linkApplicationSubjectMeta(originComment.value?.subject));
const commentRoute = computed(() => linkApplicationCommentRoute(props.application));
const commentContent = computed(() => htmlToPlainText(originComment.value?.raw || ""));
</script>

<template>
  <section v-if="origin.type === 'COMMENT'" class=":uno: border border-gray-200 rounded-lg bg-gray-50 p-3">
    <h3 class=":uno: mb-3 text-sm text-gray-900 font-medium">评论识别信息</h3>

    <div class=":uno: overflow-hidden border border-gray-100 rounded">
      <VDescription>
        <VDescriptionItem v-if="originComment" label="评论来源">
          <a
            v-if="subject?.url"
            :href="subject.url"
            target="_blank"
            rel="noopener noreferrer"
            class=":uno: inline-flex items-center gap-1 text-blue-600 hover:underline"
          >
            {{ subject.label }}
            <IconExternalLinkLine class=":uno: h-3.5 w-3.5 shrink-0" />
          </a>
          <span v-else class=":uno: text-gray-500">来源页面不可用</span>
        </VDescriptionItem>

        <VDescriptionItem label="原评论">
          <span v-if="isLoading" class=":uno: text-gray-500">正在加载评论...</span>
          <template v-else-if="originComment">
            <RouterLink v-if="commentRoute" :to="commentRoute" class=":uno: text-blue-600 hover:underline">
              打开评论管理
            </RouterLink>
          </template>
          <span v-else-if="errorState === 'forbidden'" class=":uno: text-gray-500">
            当前账号没有查看来源评论的权限
          </span>
          <span v-else-if="errorState === 'error'" class=":uno: text-gray-500">来源评论加载失败，请稍后重试</span>
          <span v-else class=":uno: text-gray-500">原评论不可用（可能已被删除）</span>
        </VDescriptionItem>

        <VDescriptionItem v-if="commentContent" label="评论内容">
          <pre
            class=":uno: max-h-60 overflow-auto whitespace-pre-wrap break-words border border-gray-200 rounded bg-white p-3 text-xs text-gray-700"
            >{{ commentContent }}</pre
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
