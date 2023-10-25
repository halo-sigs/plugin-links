<script lang="ts" setup>
import {
  VButton,
  VCard,
  VEntity,
  IconList,
  VEntityField,
  VStatusDot,
  Dialog,
  VLoading,
  VDropdownItem,
  Toast,
} from "@halo-dev/components";
import GroupEditingModal from "./GroupEditingModal.vue";
import type { LinkGroup, LinkList } from "@/types";
import { inject, ref, watch, type Ref } from "vue";
import Draggable from "vuedraggable";
import apiClient from "@/utils/api-client";
import { useLinkGroupFetch } from "@/composables/use-link";
import cloneDeep from "lodash.clonedeep";

const groupQuery = inject<Ref<string>>("groupQuery", ref(""));

const groupEditingModal = ref(false);
const selectedGroup = ref<LinkGroup>();

const { groups, isLoading, refetch } = useLinkGroupFetch();
const draggableGroups = ref<LinkGroup[]>();

watch(
  () => groups.value,
  () => {
    draggableGroups.value = cloneDeep(groups.value);
  },
  {
    immediate: true,
  }
);

const handleOpenEditingModal = (group?: LinkGroup) => {
  selectedGroup.value = group;
  groupEditingModal.value = true;
};

const onPriorityChange = async () => {
  try {
    const promises = draggableGroups.value?.map((group: LinkGroup, index) => {
      if (group.spec) {
        group.spec.priority = index;
      }
      return apiClient.put(
        `/apis/core.halo.run/v1alpha1/linkgroups/${group.metadata.name}`,
        group
      );
    });
    if (promises) {
      await Promise.all(promises);
    }
  } catch (e) {
    console.error(e);
  } finally {
    await refetch();
  }
};

const handleDelete = async (group: LinkGroup) => {
  Dialog.warning({
    title: "确定要删除该分组吗？",
    description: "将同时删除该分组下的所有链接，该操作不可恢复。",
    confirmType: "danger",
    onConfirm: async () => {
      try {
        await apiClient.delete(
          `/apis/core.halo.run/v1alpha1/linkgroups/${group.metadata.name}`
        );

        const { data } = await apiClient.get<LinkList>(
          `/apis/api.plugin.halo.run/v1alpha1/plugins/PluginLinks/links`,
          {
            params: {
              page: 0,
              size: 0,
              groupName: group.metadata.name,
            },
          }
        );

        const deleteLinkPromises = data.items.map((link) =>
          apiClient.delete(
            `/apis/core.halo.run/v1alpha1/links/${link.metadata.name}`
          )
        );

        if (deleteLinkPromises) {
          await Promise.all(deleteLinkPromises);
        }

        groupQuery.value = "";

        Toast.success("删除成功");
      } catch (e) {
        console.error("Failed to delete link group", e);
      } finally {
        await refetch();
      }
    },
  });
};

function onEditingModalClose() {
  selectedGroup.value = undefined;
  refetch();
}
</script>
<template>
  <GroupEditingModal
    v-model:visible="groupEditingModal"
    :group="selectedGroup"
    @close="onEditingModalClose"
  />
  <VCard :body-class="['!p-0']" title="分组">
    <VLoading v-if="isLoading" />
    <Transition v-else appear name="fade">
      <Draggable
        v-model="draggableGroups"
        class="links-box-border links-h-full links-w-full links-divide-y links-divide-gray-100"
        group="group"
        handle=".drag-element"
        item-key="metadata.name"
        tag="ul"
        @change="onPriorityChange"
      >
        <template #header>
          <li @click="groupQuery = ''">
            <VEntity class="links-group" :is-selected="!groupQuery">
              <template #start>
                <VEntityField title="全部"> </VEntityField>
              </template>
            </VEntity>
          </li>
        </template>
        <template #item="{ element: group }">
          <li @click="groupQuery = group.metadata.name">
            <VEntity
              :is-selected="groupQuery === group.metadata.name"
              class="links-group"
            >
              <template #prepend>
                <div
                  class="drag-element links-absolute links-inset-y-0 links-left-0 links-hidden links-w-3.5 links-cursor-move links-items-center links-bg-gray-100 links-transition-all hover:links-bg-gray-200 group-hover:links-flex"
                >
                  <IconList class="h-3.5 w-3.5" />
                </div>
              </template>

              <template #start>
                <VEntityField :title="group.spec?.displayName"></VEntityField>
              </template>

              <template #end>
                <VEntityField v-if="group.metadata.deletionTimestamp">
                  <template #description>
                    <VStatusDot v-tooltip="`删除中`" state="warning" animate />
                  </template>
                </VEntityField>
              </template>

              <template #dropdownItems>
                <VDropdownItem @click="handleOpenEditingModal(group)">
                  修改
                </VDropdownItem>
                <VDropdownItem type="danger" @click="handleDelete(group)">
                  删除
                </VDropdownItem>
              </template>
            </VEntity>
          </li>
        </template>
      </Draggable>
    </Transition>

    <template v-if="!isLoading" #item="{ element: group }" #footer>
      <Transition appear name="fade">
        <VButton
          v-permission="['plugin:links:manage']"
          block
          type="secondary"
          @click="handleOpenEditingModal(group)"
        >
          新建
        </VButton>
      </Transition>
    </template>
  </VCard>
</template>
