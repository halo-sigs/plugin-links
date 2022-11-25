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
} from "@halo-dev/components";
import GroupEditingModal from "./GroupEditingModal.vue";
import type { LinkGroup } from "@/types";
import type { LinkGroupList } from "@/types";
import { onMounted, ref } from "vue";
import Draggable from "vuedraggable";
import apiClient from "@/utils/api-client";
import { useRouteQuery } from "@vueuse/router";

const props = withDefaults(
  defineProps<{
    selectedGroup?: LinkGroup;
  }>(),
  { selectedGroup: undefined }
);

const emit = defineEmits<{
  (event: "select", group?: LinkGroup): void;
  (event: "update:selectedGroup", group?: LinkGroup): void;
}>();

const groupQuery = useRouteQuery("group");

const groups = ref<LinkGroup[]>([] as LinkGroup[]);
const loading = ref(false);
const groupEditingModal = ref(false);

const handleFetchGroups = async (options?: { mute?: boolean }) => {
  try {
    if (!options?.mute) {
      loading.value = true;
    }

    const { data } = await apiClient.get<LinkGroupList>(
      "/apis/core.halo.run/v1alpha1/linkgroups"
    );

    groups.value = data.items
      .map((group) => {
        if (group.spec) {
          group.spec.priority = group.spec.priority || 0;
        }
        return group;
      })
      .sort((a, b) => {
        return (a.spec?.priority || 0) - (b.spec?.priority || 0);
      });

    if (props.selectedGroup) {
      const updatedGroup = groups.value.find(
        (group) => group.metadata.name === props.selectedGroup?.metadata.name
      );
      if (updatedGroup) {
        emit("update:selectedGroup", updatedGroup);
      }
    }
  } catch (e) {
    console.error("Failed to fetch link groups", e);
  } finally {
    loading.value = false;
  }
};

const handleSelect = (group: LinkGroup) => {
  emit("update:selectedGroup", group);
  emit("select", group);
  groupQuery.value = group.metadata.name;
};

const handleOpenEditingModal = (group?: LinkGroup) => {
  emit("update:selectedGroup", group);
  emit("select", group);
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
    await handleFetchGroups({ mute: true });
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
        await handleFetchGroups();
      }
    },
  });
};

onMounted(async () => {
  await handleFetchGroups();

  if (groupQuery.value) {
    const group = groups.value.find(
      (m) => m.metadata.name === groupQuery.value
    );
    if (group) {
      handleSelect(group);
    }
    return;
  }

  if (groups.value.length > 0) {
    handleSelect(groups.value[0]);
  }
});

defineExpose({
  handleFetchGroups,
});
</script>
<template>
  <GroupEditingModal
    v-model:visible="groupEditingModal"
    :group="selectedGroup"
    @close="handleFetchGroups({ mute: true })"
  />
  <VCard :body-class="['!p-0']" title="分组">
    <VLoading v-if="loading" />
    <Transition v-else-if="!groups.length" appear name="fade">
      <VEmpty message="你可以尝试刷新或者新建分组" title="当前没有分组">
        <template #actions>
          <VSpace>
            <VButton size="sm" @click="handleFetchGroups"> 刷新</VButton>
          </VSpace>
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
        <template #item="{ element: group }">
          <li @click="handleSelect(group)">
            <VEntity
              :is-selected="
                selectedGroup?.metadata.name === group.metadata.name
              "
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
                  :description="`${group.spec.links?.length || 0} 个链接`"
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
                <VButton
                  v-close-popper
                  block
                  type="secondary"
                  @click="handleOpenEditingModal(group)"
                >
                  修改
                </VButton>
                <VButton
                  v-close-popper
                  block
                  type="danger"
                  @click="handleDelete(group)"
                >
                  删除
                </VButton>
              </template>
            </VEntity>
          </li>
        </template>
      </Draggable>
    </Transition>

    <template v-if="!loading" #footer>
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
