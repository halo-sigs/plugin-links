<script lang="ts" setup>
import { linksCoreApiClient } from "@/api";
import { ApiPluginHaloRunV1alpha1LinkApiListLinksRequest, Link, LinkGroup } from "@/api/generated";
import { QK_LINK_GROUPS } from "@/composables/use-group-fetch";
import { QK_LINKS } from "@/composables/use-link";
import { GroupFormState } from "@/types";
import { paginate } from "@halo-dev/api-client";
import { Dialog, Toast, VButton, VModal, VSpace } from "@halo-dev/components";
import { useMutation, useQueryClient } from "@tanstack/vue-query";
import { chunk } from "es-toolkit";
import { useTemplateRef } from "vue";
import GroupForm from "./GroupForm.vue";

const props = defineProps<{
  group: LinkGroup;
}>();

const emit = defineEmits<{
  (event: "close"): void;
}>();

const queryClient = useQueryClient();

const modal = useTemplateRef<InstanceType<typeof VModal> | null>("modal");

const { mutate, isPending } = useMutation({
  mutationFn: (data: GroupFormState) => {
    return linksCoreApiClient.group.patchLinkGroup({
      name: props.group.metadata.name,
      jsonPatchInner: [
        {
          op: "add",
          path: "/spec/displayName",
          value: data.displayName,
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
    Toast.success("编辑分组成功");
    modal.value?.close();
    queryClient.invalidateQueries({ queryKey: [QK_LINK_GROUPS] });
  },
});

function onSubmit(data: GroupFormState) {
  mutate(data);
}

function handleDelete() {
  Dialog.warning({
    title: "删除分组",
    description: "确认删除当前的分组吗，此操作会同时删除分组下的所有链接。",
    confirmType: "danger",
    onConfirm: async () => {
      await linksCoreApiClient.group.deleteLinkGroup({
        name: props.group.metadata.name,
      });

      const data = await paginate<ApiPluginHaloRunV1alpha1LinkApiListLinksRequest, Link>(
        (params) => linksCoreApiClient.link.listLink(params),
        {
          fieldSelector: [`spec.groupName=${props.group.metadata.name}`],
          size: 1000,
        },
      );

      const linkChunks = chunk(data, 5);

      for (const chunk of linkChunks) {
        await Promise.all(chunk.map((link) => linksCoreApiClient.link.deleteLink({ name: link.metadata.name })));
      }

      Toast.success("删除成功");
      modal.value?.close();
      queryClient.invalidateQueries({ queryKey: [QK_LINK_GROUPS] });
      queryClient.invalidateQueries({ queryKey: [QK_LINKS] });
    },
  });
}
</script>
<template>
  <VModal title="编辑分组" ref="modal" :mount-to-body="true" :width="600" @close="emit('close')">
    <GroupForm
      :name="group.metadata.name"
      :formState="{
        displayName: group.spec.displayName,
        annotations: group.metadata.annotations,
      }"
      @submit="onSubmit"
    />

    <template #footer>
      <div class=":uno: flex items-center justify-between">
        <VSpace>
          <!-- @vue-ignore -->
          <VButton :loading="isPending" type="secondary" @click="$formkit.submit('group-form')"> 提交 </VButton>
          <VButton @click="modal?.close()">取消</VButton>
        </VSpace>
        <VButton type="danger" ghost @click="handleDelete">删除</VButton>
      </div>
    </template>
  </VModal>
</template>
