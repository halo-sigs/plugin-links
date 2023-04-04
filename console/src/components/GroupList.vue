<script lang="ts" setup>
import {
  VButton,
  VCard,
  VEntity,
  IconList,
  VEntityField,
  VStatusDot,
  Dialog,
  VEmpty,
  VLoading,
  VDropdownItem,
} from "@halo-dev/components";
import GroupEditingModal from "./GroupEditingModal.vue";
import type { LinkGroup } from "@/types";
import { ref } from "vue";
import Draggable from "vuedraggable";
import apiClient from "@/utils/api-client";
import { useRouteQuery } from "@vueuse/router";
import { useLinkGroupFetch, useLinkFetch } from "@/composables/use-link";

const groupQuery = useRouteQuery<string>("group");

const groupEditingModal = ref(false);
const selectedGroup = ref<LinkGroup>();

const { groups, isLoading, refetch } = useLinkGroupFetch();
const { links } = useLinkFetch(ref(0), ref(0));

function getLinks(group?: LinkGroup) {
  if (!group) {
    return links.value;
  }
  return (
    links.value?.filter((link) => {
      link.spec.groupName === group.metadata.name;
    }) || []
  );
}

const handleOpenEditingModal = (group?: LinkGroup) => {
  selectedGroup.value = group;
  groupEditingModal.value = true;
};

const handleSaveInBatch = async () => {
  try {
    const promises = groups.value?.map((group: LinkGroup, index) => {
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

        const deleteItemsPromises = group.spec?.links.map((item) =>
          apiClient.delete(`/apis/core.halo.run/v1alpha1/links/${item}`)
        );

        if (deleteItemsPromises) {
          await Promise.all(deleteItemsPromises);
        }
      } catch (e) {
        console.error("Failed to delete link group", e);
      } finally {
        await refetch();
      }
    },
  });
};
</script>
<template>
  <GroupEditingModal
    v-model:visible="groupEditingModal"
    :group="selectedGroup"
    @close="refetch"
  />
  <VCard :body-class="['!p-0']" title="分组">
    <VLoading v-if="isLoading" />
    <Transition v-else-if="!groups?.length" appear name="fade">
      <VEmpty message="你可以尝试刷新或者新建分组" title="当前没有分组">
        <template #actions>
          <VButton size="sm" @click="refetch"> 刷新</VButton>
        </template>
      </VEmpty>
    </Transition>
    <Transition v-else appear name="fade">
      <Draggable
        v-model="groups"
        class="links-box-border links-h-full links-w-full links-divide-y links-divide-gray-100"
        group="group"
        handle=".drag-element"
        item-key="metadata.name"
        tag="ul"
        @change="handleSaveInBatch"
      >
        <template #header>
          <li @click="groupQuery = ''">
            <VEntity class="links-group" :is-selected="!groupQuery">
              <template #start>
                <VEntityField
                  title="全部"
                  :description="`${getLinks()?.length || 0} 个链接`"
                >
                </VEntityField>
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
                <VEntityField
                  :title="group.spec?.displayName"
                  :description="`${getLinks(group)?.length || 0} 个链接`"
                ></VEntityField>
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

    <template v-if="!isLoading" #footer>
      <Transition appear name="fade">
        <VButton
          v-permission="['plugin:links:manage']"
          block
          type="secondary"
          @click="handleOpenEditingModal(undefined)"
        >
          新增分组
        </VButton>
      </Transition>
    </template>
  </VCard>
</template>
