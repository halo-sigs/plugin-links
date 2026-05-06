<script lang="ts" setup>
import { linksCoreApiClient } from "@/api";
import { LinkGroup } from "@/api/generated";
import { QK_LINK_GROUPS } from "@/composables/use-group-fetch";
import { QK_LINKS } from "@/composables/use-link";
import { LinkFormState } from "@/types";
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
    const {
      data: { total },
    } = await linksCoreApiClient.link.listLink({
      page: 1,
      size: 1,
    });

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
          priority: total + 1,
        },
        metadata: {
          name: "",
          generateName: "link-",
          annotations: data.annotations,
        },
      },
    });
  },
  onSuccess: () => {
    Toast.success("创建链接成功");
    modal.value?.close();
    queryClient.invalidateQueries({ queryKey: [QK_LINK_GROUPS] });
    queryClient.invalidateQueries({ queryKey: [QK_LINKS] });
  },
});

function onSubmit(data: LinkFormState) {
  mutate(data);
}

const title = computed(() => {
  return [`创建链接`, props.group?.spec?.displayName].filter(Boolean).join(" - ");
});
</script>
<template>
  <VModal :title="title" ref="modal" :mount-to-body="true" :width="650" @close="emit('close')">
    <LinkForm @submit="onSubmit" />

    <template #footer>
      <VSpace>
        <!-- @vue-ignore -->
        <VButton :loading="isPending" type="secondary" @click="$formkit.submit('link-form')"> 提交 </VButton>
        <VButton @click="modal?.close()">取消</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
