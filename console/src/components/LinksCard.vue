<script lang="ts" setup>
import { linksCoreApiClient } from "@/api";
import { LinkGroup } from "@/api/generated";
import { QK_LINK_GROUPS, useLinkGroupFetch } from "@/composables/use-group-fetch";
import { GroupWithLinks, QK_GROUPS_WITH_LINKS } from "@/composables/use-link-fetch";
import { Dialog, Toast, VButton, VCard, VDropdown, VDropdownItem, VEmpty, VSpace } from "@halo-dev/components";
import { useQueryClient } from "@tanstack/vue-query";
import { chunk } from "es-toolkit";
import { computed, defineAsyncComponent, ref } from "vue";
import LinkBadge from "./LinkBadge.vue";
import LinksSortableCard from "./LinksSortableCard.vue";

const GroupEditingModal = defineAsyncComponent(
  () => import(/* webpackChunkName: "group-editing-modal" */ "./GroupEditingModal.vue"),
);
const LinkCreationModal = defineAsyncComponent(
  () => import(/* webpackChunkName: "link-creation-modal" */ "./LinkCreationModal.vue"),
);

const props = defineProps<{
  groupWithLinks: GroupWithLinks;
}>();

const group = computed(() => props.groupWithLinks.group);
const links = computed(() => props.groupWithLinks.links);

const queryClient = useQueryClient();

const { data: groups } = useLinkGroupFetch();

const creationModalVisible = ref(false);

const groupEditingModalVisible = ref(false);

const enabledSort = ref(false);
const enabledSelect = ref(false);

const selectedLinkNames = ref<string[]>([]);

function handleSelectAll() {
  selectedLinkNames.value = links.value.map((link) => link.metadata.name);
}

function handleDeleteInBatch() {
  Dialog.warning({
    title: "是否确认删除选中的链接？",
    description: "删除之后将无法恢复。",
    confirmType: "danger",
    onConfirm: async () => {
      const chunks = chunk(selectedLinkNames.value, 5);

      for (const chunk of chunks) {
        await Promise.all(
          chunk.map((linkName) =>
            linksCoreApiClient.link.deleteLink({
              name: linkName,
            }),
          ),
        );
      }

      Toast.success("删除成功");
      queryClient.invalidateQueries({ queryKey: [QK_GROUPS_WITH_LINKS] });
      enabledSelect.value = false;
    },
  });
}

function handleMoveToGroup(group: LinkGroup) {
  Dialog.warning({
    title: "移动到分组",
    description: `确认将选中的链接移动到${group.spec?.displayName}分组吗？`,
    confirmType: "danger",
    onConfirm: async () => {
      const chunks = chunk(selectedLinkNames.value, 5);

      for (const chunk of chunks) {
        await Promise.all(
          chunk.map((linkName) =>
            linksCoreApiClient.link.patchLink({
              name: linkName,
              jsonPatchInner: [{ op: "add", path: "/spec/groupName", value: group.metadata.name }],
            }),
          ),
        );
      }

      Toast.success("移动成功");
      queryClient.invalidateQueries({ queryKey: [QK_LINK_GROUPS] });
      queryClient.invalidateQueries({ queryKey: [QK_GROUPS_WITH_LINKS] });
      enabledSelect.value = false;
    },
  });
}
</script>
<template>
  <LinksSortableCard v-if="enabledSort" :group-with-links="groupWithLinks" @close="enabledSort = false" />
  <VCard v-else>
    <template #header>
      <div class=":uno: group w-full flex flex-wrap items-center justify-between gap-2 px-4 py-2">
        <div class=":uno: flex flex-wrap items-center gap-3">
          <div class=":uno: text-sm text-gray-900 font-semibold">
            {{ group?.spec?.displayName || "未分组" }}
          </div>
          <VSpace v-if="enabledSelect" class=":uno: flex-wrap">
            <VButton size="sm" @click="handleSelectAll">全选</VButton>
            <VButton size="sm" @click="selectedLinkNames.length = 0">清空选择</VButton>
            <VDropdown>
              <VButton size="sm">移动</VButton>
              <template #popper>
                <VDropdownItem
                  @click="handleMoveToGroup(item)"
                  v-for="item in groups"
                  :key="item.metadata.name"
                  :value="item.metadata.name"
                >
                  {{ item.spec?.displayName }}
                </VDropdownItem>
              </template>
            </VDropdown>
            <VButton size="sm" type="danger" @click="handleDeleteInBatch">删除</VButton>
            <VButton size="sm" @click="enabledSelect = false">取消</VButton>
          </VSpace>
          <VSpace
            v-else
            class=":uno: opacity-0 transition-opacity [@media(hover:none)]:opacity-100 group-hover:opacity-100"
          >
            <VButton v-if="links.length && group" size="sm" @click="enabledSort = true">排序</VButton>
            <VButton v-if="links.length" size="sm" @click="enabledSelect = true">选择</VButton>
            <VButton v-if="group" size="sm" @click="groupEditingModalVisible = true">编辑分组</VButton>
          </VSpace>
        </div>
        <div class=":uno: opacity-0 transition-opacity [@media(hover:none)]:opacity-100 group-hover:opacity-100">
          <VButton type="secondary" size="sm" @click="creationModalVisible = true">新建</VButton>
        </div>
      </div>
    </template>
    <VEmpty v-if="!links.length" title="无数据" message="此分组下暂无链接">
      <template #actions>
        <VButton type="secondary" size="sm" @click="creationModalVisible = true">新建</VButton>
      </template>
    </VEmpty>
    <div class=":uno: flex flex-wrap gap-2" v-else>
      <LinkBadge v-for="link in links" :key="link.metadata.name" :link="link" :select-mode="enabledSelect">
        <template #checkbox>
          <input type="checkbox" v-model="selectedLinkNames" :value="link.metadata.name" />
        </template>
      </LinkBadge>
    </div>
  </VCard>

  <LinkCreationModal v-if="creationModalVisible" :group="group" @close="creationModalVisible = false" />

  <GroupEditingModal
    v-if="groupEditingModalVisible && group"
    :group="group"
    @close="groupEditingModalVisible = false"
  />
</template>
