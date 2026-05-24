<script lang="ts" setup>
import { linksConsoleApiClient, linksCoreApiClient } from "@/api";
import type { LinkGroup } from "@/api/generated";
import { QK_GROUPS_WITH_LINKS, QK_RSS_GROUPS_WITH_LINKS } from "@/composables/use-link-fetch";
import type { LinkFormState } from "@/types";
import { Toast, VButton, VModal, VSpace } from "@halo-dev/components";
import { useMutation, useQueryClient } from "@tanstack/vue-query";
import { computed, useTemplateRef } from "vue";
import LinkForm from "./LinkForm.vue";

const props = defineProps<{
  group?: LinkGroup;
}>();

const emit = defineEmits<{
  (event: "close"): void;
}>();

const queryClient = useQueryClient();

const modal = useTemplateRef<InstanceType<typeof VModal> | null>("modal");

const { mutate, isPending } = useMutation({
  mutationFn: async (data: LinkFormState) => {
    // query the max priority
    const { data: linkList } = await linksCoreApiClient.link.listLink({
      page: 1,
      size: 1,
      sort: ["spec.priority,desc"],
    });
    const maxPriority = linkList.items[0]?.spec?.priority || 0;

    return linksCoreApiClient.link.createLink({
      link: {
        apiVersion: "core.halo.run/v1alpha1",
        kind: "Link",
        spec: {
          url: data.url,
          displayName: data.displayName,
          logo: data.logo,
          description: data.description,
          groupName: props.group?.metadata.name,
          priority: maxPriority + 1,
          rss:
            data.rss?.enabled || data.rss?.feedUrls?.length
              ? {
                  enabled: data.rss.enabled ?? false,
                  feedUrls: data.rss.feedUrls || [],
                }
              : undefined,
        },
        metadata: {
          name: "",
          generateName: "link-",
          annotations: data.annotations,
        },
      },
    });
  },
  onSuccess: async (response, data) => {
    Toast.success("创建链接成功");
    const linkName = response.data.metadata.name;
    if (shouldRefreshFeedAfterSave(data) && linkName) {
      try {
        await linksConsoleApiClient.feed.refreshLinkFeed({ name: linkName });
        Toast.success("RSS 已自动刷新");
      } catch {
        // Halo's API interceptor shows request failure toasts.
      }
    }
    modal.value?.close();
    queryClient.invalidateQueries({ queryKey: [QK_GROUPS_WITH_LINKS] });
    queryClient.invalidateQueries({ queryKey: [QK_RSS_GROUPS_WITH_LINKS] });
  },
});

function onSubmit(data: LinkFormState) {
  mutate(data);
}

function shouldRefreshFeedAfterSave(data: LinkFormState) {
  return Boolean(data.rss?.enabled && data.rss.feedUrls?.length);
}

const title = computed(() => {
  return [`创建链接`, props.group?.spec?.displayName].filter(Boolean).join(" - ");
});
</script>
<template>
  <VModal :centered="false" :title="title" ref="modal" :mount-to-body="true" :width="650" @close="emit('close')">
    <LinkForm @submit="onSubmit" />

    <template #footer>
      <VSpace>
        <!-- @vue-ignore -->
        <VButton :loading="isPending" type="secondary" @click="$formkit.submit('link-form')"> 保存 </VButton>
        <VButton @click="modal?.close()">取消</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
