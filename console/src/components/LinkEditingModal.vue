<script lang="ts" setup>
import { linksConsoleApiClient, linksCoreApiClient } from "@/api";
import type { Link } from "@/api/generated";
import { QK_LINK_GROUPS } from "@/composables/use-group-fetch";
import { QK_GROUPS_WITH_LINKS, QK_RSS_GROUPS_WITH_LINKS } from "@/composables/use-link-fetch";
import type { LinkFormState } from "@/types";
import { Dialog, Toast, VButton, VModal, VSpace } from "@halo-dev/components";
import { useMutation, useQueryClient } from "@tanstack/vue-query";
import { shallowRef, useTemplateRef } from "vue";
import LinkForm from "./LinkForm.vue";

const props = withDefaults(
  defineProps<{
    link: Link;
  }>(),
  {},
);

const emit = defineEmits<{
  (event: "close"): void;
}>();

const queryClient = useQueryClient();

const modal = useTemplateRef<InstanceType<typeof VModal> | null>("modal");
const isRefreshingFeed = shallowRef(false);

const { mutate, isPending } = useMutation({
  mutationFn: (data: LinkFormState) => {
    return linksCoreApiClient.link.patchLink({
      name: props.link.metadata.name,
      jsonPatchInner: [
        {
          op: "add",
          path: "/spec/url",
          value: data.url,
        },
        {
          op: "add",
          path: "/spec/displayName",
          value: data.displayName,
        },
        {
          op: "add",
          path: "/spec/logo",
          value: data.logo || "",
        },
        {
          op: "add",
          path: "/spec/description",
          value: data.description || "",
        },
        {
          op: "add",
          path: "/spec/rss",
          value:
            data.rss?.enabled || data.rss?.feedUrls?.length
              ? {
                  enabled: data.rss.enabled ?? false,
                  feedUrls: data.rss.feedUrls || [],
                }
              : null,
        },
        {
          op: "add",
          path: "/metadata/annotations",
          value: data.annotations || {},
        },
      ],
    });
  },
  onSuccess: async (_, data) => {
    Toast.success("编辑链接成功");
    if (shouldRefreshFeedAfterSave(data)) {
      try {
        await linksConsoleApiClient.feed.refreshLinkFeed({
          name: props.link.metadata.name,
        });
        Toast.success("RSS 已自动刷新");
      } catch {
        // Halo's API interceptor shows request failure toasts.
      }
    }
    modal.value?.close();
    queryClient.invalidateQueries({ queryKey: [QK_LINK_GROUPS] });
    queryClient.invalidateQueries({ queryKey: [QK_GROUPS_WITH_LINKS] });
    queryClient.invalidateQueries({ queryKey: [QK_RSS_GROUPS_WITH_LINKS] });
  },
});

function onSubmit(data: LinkFormState) {
  mutate(data);
}

function shouldRefreshFeedAfterSave(data: LinkFormState) {
  const nextFeedUrls = data.rss?.feedUrls || [];
  return Boolean(
    data.rss?.enabled
      && nextFeedUrls.length
      && (!props.link.spec?.rss?.enabled || !sameFeedUrls(props.link.spec.rss.feedUrls || [], nextFeedUrls)),
  );
}

async function handleRefreshFeed() {
  if (!props.link.spec?.rss?.enabled || !props.link.spec.rss.feedUrls?.length || isRefreshingFeed.value) {
    return;
  }
  isRefreshingFeed.value = true;
  try {
    await linksConsoleApiClient.feed.refreshLinkFeed({
      name: props.link.metadata.name,
    });
    Toast.success("刷新 RSS 成功");
    queryClient.invalidateQueries({ queryKey: [QK_GROUPS_WITH_LINKS] });
    queryClient.invalidateQueries({ queryKey: [QK_RSS_GROUPS_WITH_LINKS] });
  } catch {
    // Halo's API interceptor shows request failure toasts.
  } finally {
    isRefreshingFeed.value = false;
  }
}

function sameFeedUrls(left: string[], right: string[]) {
  return left.length === right.length && left.every((feedUrl, index) => feedUrl === right[index]);
}

function handleDelete() {
  Dialog.warning({
    title: "是否确认删除当前的链接？",
    description: "删除之后将无法恢复。",
    confirmType: "danger",
    onConfirm: async () => {
      await linksCoreApiClient.link.deleteLink({
        name: props.link.metadata.name,
      });

      Toast.success("删除成功");

      modal.value?.close();
      queryClient.invalidateQueries({ queryKey: [QK_GROUPS_WITH_LINKS] });
      queryClient.invalidateQueries({ queryKey: [QK_RSS_GROUPS_WITH_LINKS] });
    },
  });
}
</script>
<template>
  <VModal :centered="false" title="编辑链接" ref="modal" :mount-to-body="true" :width="650" @close="emit('close')">
    <template #actions>
      <slot name="append-actions" />
    </template>

    <div>
      <LinkForm
        :key="link.metadata.name"
        :name="link.metadata.name"
        :formState="{
          url: link.spec.url,
          displayName: link.spec.displayName,
          logo: link.spec.logo,
          description: link.spec.description,
          rss: link.spec.rss,
          annotations: link.metadata.annotations,
        }"
        @submit="onSubmit"
      />
    </div>

    <template #footer>
      <div class=":uno: flex items-center justify-between">
        <VSpace>
          <!-- @vue-ignore -->
          <VButton :loading="isPending" type="secondary" @click="$formkit.submit('link-form')"> 保存 </VButton>
          <VButton
            v-if="link.spec?.rss?.enabled && link.spec.rss.feedUrls?.length"
            :loading="isRefreshingFeed"
            @click="handleRefreshFeed"
          >
            刷新 RSS
          </VButton>
          <VButton @click="modal?.close()">取消</VButton>
        </VSpace>
        <VButton type="danger" ghost @click="handleDelete">删除</VButton>
      </div>
    </template>
  </VModal>
</template>
