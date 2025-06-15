<script lang="ts" setup>
import { linksConsoleApiClient, linksCoreApiClient } from "@/api";
import { LinkGroup } from "@/api/generated";
import { useLinkGroupFetch } from "@/composables/use-link";
import {
  Dialog,
  IconList,
  Toast,
  VButton,
  VCard,
  VDropdownItem,
  VEntity,
  VEntityField,
  VLoading,
  VStatusDot,
} from "@halo-dev/components";
import { inject, ref, type Ref } from "vue";
import GroupEditingModal from "./GroupEditingModal.vue";
import { VueDraggable } from "vue-draggable-plus";

const groupQuery = inject<Ref<string>>("groupQuery", ref(""));

const groupEditingModal = ref(false);
const selectedGroup = ref<LinkGroup>();

const { groups, isLoading, refetch } = useLinkGroupFetch();

const handleOpenEditingModal = (group?: LinkGroup) => {
  selectedGroup.value = group;
  groupEditingModal.value = true;
};

const onPriorityChange = async () => {
  try {
    const promises = groups.value?.map((group: LinkGroup, index) => {
      if (group.spec) {
        group.spec.priority = index;
      }
      return linksCoreApiClient.group.updateLinkGroup({
        name: group.metadata.name,
        linkGroup: group,
      });
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
        await linksCoreApiClient.group.deleteLinkGroup({ name: group.metadata.name });

        const { data } = await linksConsoleApiClient.link.listLinks({
          page: 0,
          size: 0,
          groupName: group.metadata.name,
        });

        const deleteLinkPromises = data.items.map((link) =>
          linksCoreApiClient.link.deleteLink({
            name: link.metadata.name,
          })
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
  groupEditingModal.value = false;
  selectedGroup.value = undefined;
  refetch();
}
</script>
<template>
  <GroupEditingModal v-if="groupEditingModal" :group="selectedGroup" @close="onEditingModalClose" />
  <VCard :body-class="[':uno: !p-0']" title="分组">
    <VLoading v-if="isLoading" />
    <Transition v-else appear name="fade">
      <div class=":uno: w-full overflow-x-auto">
        <table class=":uno: w-full border-spacing-0">
          <VEntity class=":uno: group" :is-selected="!groupQuery" @click="groupQuery = ''">
            <template #start>
              <VEntityField title="全部"> </VEntityField>
            </template>
          </VEntity>
          <VueDraggable
            v-model="groups"
            class=":uno: divide-y divide-gray-100"
            group="group"
            handle=".drag-element"
            item-key="metadata.name"
            tag="tbody"
            @update="onPriorityChange"
          >
            <VEntity
              v-for="group in groups"
              :key="group.metadata.name"
              :is-selected="groupQuery === group.metadata.name"
              class=":uno: group"
              @click="groupQuery = group.metadata.name"
            >
              <template #prepend>
                <div
                  class=":uno: drag-element absolute inset-y-0 left-0 hidden w-3.5 cursor-move items-center bg-gray-100 transition-all group-hover:flex hover:bg-gray-200"
                >
                  <IconList class=":uno: h-3.5 w-3.5" />
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
                <VDropdownItem @click="handleOpenEditingModal(group)"> 修改 </VDropdownItem>
                <VDropdownItem type="danger" @click="handleDelete(group)"> 删除 </VDropdownItem>
              </template>
            </VEntity>
          </VueDraggable>
        </table>
      </div>
    </Transition>

    <template v-if="!isLoading" #footer>
      <Transition appear name="fade">
        <!-- @unocss-skip-start -->
        <VButton
          v-permission="['plugin:links:manage']"
          block
          type="secondary"
          @click="handleOpenEditingModal(undefined)"
        >
          新建
        </VButton>
        <!-- @unocss-skip-end -->
      </Transition>
    </template>
  </VCard>
</template>
