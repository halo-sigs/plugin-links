<script lang="ts" setup>
import { linksConsoleApiClient, linksCoreApiClient } from "@/api";
import { Link, LinkGroupVo } from "@/api/generated";
import { QK_LINK_GROUPS, useLinkGroupFetch } from "@/composables/use-group-fetch";
import { GroupWithLinks, QK_GROUPS_WITH_LINKS } from "@/composables/use-link-fetch";
import {
  Dialog,
  IconArrowLeft,
  IconArrowRight,
  IconMore,
  Toast,
  VButton,
  VCard,
  VDropdown,
  VDropdownItem,
  VEmpty,
  VSpace,
} from "@halo-dev/components";
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
const LinkEditingModal = defineAsyncComponent(
  () => import(/* webpackChunkName: "link-editing-modal" */ "./LinkEditingModal.vue"),
);

const props = defineProps<{
  groupWithLinks: GroupWithLinks;
}>();

const group = computed(() => props.groupWithLinks.group);
const links = computed(() => props.groupWithLinks.links);

const queryClient = useQueryClient();

const { data: groups } = useLinkGroupFetch();

const otherGroups = computed(() => groups.value?.filter((i) => i.metadata.name !== group.value?.metadata.name));

const creationModalVisible = ref(false);

const groupEditingModalVisible = ref(false);

const enabledSort = ref(false);
const enabledSelect = ref(false);

const selectedLinkNames = ref<string[]>([]);

const selectedLink = ref<Link | undefined>();
const linkEditingModalVisible = ref(false);

function handleOpenEdit(link: Link) {
  selectedLink.value = link;
  linkEditingModalVisible.value = true;
}

function handleSelectPrevious() {
  if (!links.value.length) return;
  const currentIndex = links.value.findIndex((link) => link.metadata.name === selectedLink.value?.metadata.name);
  if (currentIndex > 0) {
    selectedLink.value = links.value[currentIndex - 1];
  } else if (currentIndex <= 0) {
    selectedLink.value = undefined;
  }
}

function handleSelectNext() {
  if (!links.value.length) return;
  if (!selectedLink.value) {
    selectedLink.value = links.value[0];
    return;
  }
  const currentIndex = links.value.findIndex((link) => link.metadata.name === selectedLink.value?.metadata.name);
  if (currentIndex !== links.value.length - 1) {
    selectedLink.value = links.value[currentIndex + 1];
  }
}

function handleEditingModalClose() {
  linkEditingModalVisible.value = false;
  selectedLink.value = undefined;
}

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
      selectedLinkNames.value.length = 0;
    },
  });
}

function handleMoveToGroup(group: LinkGroupVo) {
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
      selectedLinkNames.value.length = 0;
    },
  });
}

function handleDelete({ deleteLinks }: { deleteLinks: boolean }) {
  const title = deleteLinks ? "删除分组及链接" : "仅删除分组";
  const description = deleteLinks
    ? `将同时删除该分组下所有链接，此操作不可恢复。`
    : `该分组下的链接将变为未分组，此操作不可恢复。`;

  Dialog.warning({
    title,
    description,
    confirmType: "danger",
    onConfirm: async () => {
      if (!group.value) {
        return;
      }
      await linksConsoleApiClient.group.deleteLinkGroup({
        name: group.value.metadata.name,
        deleteLinks,
      });
      Toast.success("删除成功");
      queryClient.invalidateQueries({ queryKey: [QK_LINK_GROUPS] });
      queryClient.invalidateQueries({ queryKey: [QK_GROUPS_WITH_LINKS] });
    },
  });
}
</script>
<template>
  <LinksSortableCard v-if="enabledSort" :group-with-links="groupWithLinks" @close="enabledSort = false" />
  <VCard v-else>
    <template #header>
      <div class=":uno: w-full flex flex-wrap items-center justify-between gap-2 px-4 py-2">
        <div class=":uno: flex flex-wrap items-center gap-3">
          <div class=":uno: text-sm text-gray-900 font-semibold">
            {{ group?.spec?.displayName || "未分组" }}（{{ links.length }}）
          </div>
          <VSpace v-if="enabledSelect" class=":uno: flex-wrap">
            <VButton size="sm" @click="handleSelectAll">全选</VButton>
            <VButton size="sm" @click="selectedLinkNames.length = 0">清空选择</VButton>
            <VDropdown>
              <VButton size="sm" :disabled="selectedLinkNames.length === 0 || !otherGroups?.length">移动</VButton>
              <template #popper>
                <VDropdownItem
                  @click="handleMoveToGroup(item)"
                  v-for="item in otherGroups"
                  :key="item.metadata.name"
                  :value="item.metadata.name"
                >
                  {{ item.spec?.displayName }}
                </VDropdownItem>
              </template>
            </VDropdown>
            <VButton size="sm" type="danger" :disabled="selectedLinkNames.length === 0" @click="handleDeleteInBatch">
              删除
            </VButton>
            <VButton size="sm" @click="enabledSelect = false">取消</VButton>
          </VSpace>
          <VSpace v-else class=":uno: flex-wrap">
            <VDropdown v-if="links.length || group">
              <VButton size="sm" ghost>
                <IconMore />
              </VButton>
              <template #popper>
                <VDropdownItem v-if="links.length" @click="enabledSort = true">排序</VDropdownItem>
                <VDropdownItem v-if="links.length" @click="enabledSelect = true">批量选择</VDropdownItem>
                <VDropdownItem v-if="group" @click="groupEditingModalVisible = true">编辑分组</VDropdownItem>
                <VDropdown>
                  <VDropdownItem v-if="group" type="danger">删除分组</VDropdownItem>
                  <template #popper>
                    <VDropdownItem v-if="group" type="danger" @click="handleDelete({ deleteLinks: false })">
                      仅删除分组
                    </VDropdownItem>
                    <VDropdownItem v-if="group" type="danger" @click="handleDelete({ deleteLinks: true })">
                      删除分组及链接
                    </VDropdownItem>
                  </template>
                </VDropdown>
              </template>
            </VDropdown>
          </VSpace>
        </div>
        <div>
          <VButton type="secondary" size="sm" @click="creationModalVisible = true">新建</VButton>
        </div>
      </div>
    </template>
    <VEmpty v-if="!links.length" title="无数据" message="此分组下暂无链接">
      <template #actions>
        <VButton type="secondary" size="sm" @click="creationModalVisible = true">新建</VButton>
      </template>
    </VEmpty>
    <div class=":uno: grid grid-cols-2 gap-2.5 2xl:grid-cols-7 lg:grid-cols-4 md:grid-cols-3 xl:grid-cols-6" v-else>
      <LinkBadge
        v-for="link in links"
        :key="link.metadata.name"
        :link="link"
        :select-mode="enabledSelect"
        @open-edit="handleOpenEdit(link)"
      >
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

  <LinkEditingModal
    v-if="linkEditingModalVisible && selectedLink"
    :link="selectedLink"
    @close="handleEditingModalClose"
  >
    <template #append-actions>
      <span @click="handleSelectPrevious">
        <IconArrowLeft />
      </span>
      <span @click="handleSelectNext">
        <IconArrowRight />
      </span>
    </template>
  </LinkEditingModal>
</template>
