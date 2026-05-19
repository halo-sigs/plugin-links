<script lang="ts" setup>
import { linksCoreApiClient } from "@/api";
import type { Link } from "@/api/generated";
import { QK_LINK_GROUPS } from "@/composables/use-group-fetch";
import { QK_GROUPS_WITH_LINKS } from "@/composables/use-link-fetch";
import type { LinkFormState } from "@/types";
import { Dialog, Toast, VButton, VModal, VSpace } from "@halo-dev/components";
import { useMutation, useQueryClient } from "@tanstack/vue-query";
import { useTemplateRef } from "vue";
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
          path: "/metadata/annotations",
          value: data.annotations || {},
        },
      ],
    });
  },
  onSuccess: () => {
    Toast.success("编辑链接成功");
    modal.value?.close();
    queryClient.invalidateQueries({ queryKey: [QK_LINK_GROUPS] });
    queryClient.invalidateQueries({ queryKey: [QK_GROUPS_WITH_LINKS] });
  },
});

function onSubmit(data: LinkFormState) {
  mutate(data);
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
          <VButton @click="modal?.close()">取消</VButton>
        </VSpace>
        <VButton type="danger" ghost @click="handleDelete">删除</VButton>
      </div>
    </template>
  </VModal>
</template>
