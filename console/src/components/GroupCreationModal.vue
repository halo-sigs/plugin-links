<script lang="ts" setup>
import { linksCoreApiClient } from "@/api";
import { QK_LINK_GROUPS } from "@/composables/use-group-fetch";
import { QK_GROUPS_WITH_LINKS } from "@/composables/use-link-fetch";
import { GroupFormState } from "@/types";
import { Toast, VButton, VModal, VSpace } from "@halo-dev/components";
import { useMutation, useQueryClient } from "@tanstack/vue-query";
import { useTemplateRef } from "vue";
import GroupForm from "./GroupForm.vue";

const emit = defineEmits<{
  (event: "close"): void;
}>();

const queryClient = useQueryClient();

const modal = useTemplateRef<InstanceType<typeof VModal> | null>("modal");

const { mutate, isPending } = useMutation({
  mutationFn: async (data: GroupFormState) => {
    // query the max priority
    const {
      data: { total },
    } = await linksCoreApiClient.group.listLinkGroup({
      page: 1,
      size: 1,
    });

    return linksCoreApiClient.group.createLinkGroup({
      linkGroup: {
        apiVersion: "core.halo.run/v1alpha1",
        kind: "LinkGroup",
        metadata: {
          name: "",
          generateName: "link-group-",
          annotations: data.annotations,
        },
        spec: {
          displayName: data.displayName,
          priority: total + 1,
          links: [],
        },
      },
    });
  },
  onSuccess: () => {
    Toast.success("创建分组成功");
    modal.value?.close();
    queryClient.invalidateQueries({ queryKey: [QK_LINK_GROUPS] });
    queryClient.invalidateQueries({ queryKey: [QK_GROUPS_WITH_LINKS] });
  },
});

function onSubmit(data: GroupFormState) {
  mutate(data);
}
</script>
<template>
  <VModal title="新建分组" ref="modal" :mount-to-body="true" :width="600" @close="emit('close')">
    <GroupForm @submit="onSubmit" />

    <template #footer>
      <VSpace>
        <!-- @vue-ignore -->
        <VButton :loading="isPending" type="secondary" @click="$formkit.submit('group-form')"> 提交 </VButton>
        <VButton @click="modal?.close()">取消</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
