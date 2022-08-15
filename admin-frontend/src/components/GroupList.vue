<script lang="ts" setup>
import {
  IconList,
  IconSettings,
  useDialog,
  VButton,
  VCard,
  VSpace,
} from "@halo-dev/components";
import GroupEditingModal from "./GroupEditingModal.vue";
import type { LinkGroup } from "@/types";
import { LinkGroupList } from "@/types";
import { onMounted, ref } from "vue";
import Draggable from "vuedraggable";
import apiClient from "@/utils/api-client";
import { useRouteQuery } from "@vueuse/router";

const props = withDefaults(
  defineProps<{
    selectedGroup: LinkGroup | null;
  }>(),
  { selectedGroup: null }
);

const emit = defineEmits<{
  (event: "select", group: LinkGroup): void;
  (event: "update:selectedGroup", group: LinkGroup): void;
}>();

const groupQuery = useRouteQuery("group");
const dialog = useDialog();

const groups = ref<LinkGroup[]>([] as LinkGroup[]);
const groupEditingModal = ref(false);

const handleFetchGroups = async () => {
  try {
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
  }
};

const handleSelect = (group: LinkGroup) => {
  emit("update:selectedGroup", group);
  emit("select", group);
  groupQuery.value = group.metadata.name;
};

const handleOpenEditingModal = (group: LinkGroup | null) => {
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
    await handleFetchGroups();
  }
};

const handleDelete = async (group: LinkGroup) => {
  dialog.warning({
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
    @close="handleFetchGroups"
  />
  <VCard :bodyClass="['!p-0']" title="分组">
    <Draggable
      v-model="groups"
      class="links-divide-y links-divide-gray-100 links-bg-white"
      group="group"
      handle=".drag-element"
      item-key="metadata.name"
      tag="div"
      @change="handleSaveInBatch"
    >
      <template #item="{ element }">
        <div
          :class="{
            'links-bg-gray-50':
              selectedGroup?.metadata.name === element.metadata.name,
          }"
          class="links-relative links-flex links-items-center links-p-4"
          @click="handleSelect(element)"
        >
          <div>
            <IconList class="drag-element links-cursor-move" />
          </div>
          <span
            class="links-ml-3 links-flex links-flex-1 links-cursor-pointer links-flex-col"
          >
            <span class="links-block links-text-sm links-font-medium">
              {{ element.spec?.displayName }}
            </span>
            <span class="links-block links-text-sm links-text-gray-400">
              {{ element.spec.links?.length }} 个
            </span>
          </span>
          <FloatingTooltip
            v-if="element.metadata.deletionTimestamp"
            class="links-mr-4 links-hidden links-items-center sm:links-flex"
          >
            <div
              class="links-inline-flex links-h-1.5 links-w-1.5 links-rounded-full links-bg-red-600"
            >
              <span
                class="links-inline-block links-h-1.5 links-w-1.5 links-animate-ping links-rounded-full links-bg-red-600"
              ></span>
            </div>
            <template #popper> 删除中</template>
          </FloatingTooltip>
          <div v-permission="['plugin:links:manage']" class="links-self-center">
            <FloatingDropdown>
              <IconSettings
                class="links-cursor-pointer links-transition-all hover:links-text-blue-600"
              />
              <template #popper>
                <div class="links-w-48 links-p-2">
                  <VSpace class="links-w-full" direction="column">
                    <VButton
                      block
                      type="secondary"
                      @click="handleOpenEditingModal(element)"
                    >
                      修改
                    </VButton>
                    <VButton block type="danger" @click="handleDelete(element)">
                      删除
                    </VButton>
                  </VSpace>
                </div>
              </template>
            </FloatingDropdown>
          </div>
        </div>
      </template>
    </Draggable>

    <template #footer>
      <VButton
        v-permission="['plugin:links:manage']"
        block
        type="secondary"
        @click="handleOpenEditingModal(null)"
      >
        新增分组
      </VButton>
    </template>
  </VCard>
</template>
