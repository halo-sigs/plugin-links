<script lang="ts" setup>
import { linksCoreApiClient } from "@/api";
import { Link, LinkGroup } from "@/api/generated";
import { useLinkFetch, useLinkGroupFetch } from "@/composables/use-link";
import { formatDatetime } from "@/utils/date";
import {
  Dialog,
  IconAddCircle,
  IconArrowLeft,
  IconArrowRight,
  IconList,
  Toast,
  VAvatar,
  VButton,
  VCard,
  VDropdown,
  VDropdownDivider,
  VDropdownItem,
  VEmpty,
  VEntity,
  VEntityField,
  VLoading,
  VPageHeader,
  VPagination,
  VSpace,
  VStatusDot,
} from "@halo-dev/components";
import { useQueryClient } from "@tanstack/vue-query";
import { useFileSystemAccess } from "@vueuse/core";
import { useRouteQuery } from "@vueuse/router";
import { provide, ref, watch, type Ref } from "vue";
import yaml from "yaml";
import RiLinksLine from "~icons/ri/links-line";
import GroupList from "../components/GroupList.vue";
import LinkEditingModal from "../components/LinkEditingModal.vue";
import { VueDraggable } from "vue-draggable-plus";

const queryClient = useQueryClient();

const drag = ref(false);
const selectedLink = ref<Link | undefined>();
const selectedLinks = ref<string[]>([]);
const editingModal = ref(false);
const checkedAll = ref(false);

const groupQuery = useRouteQuery<string>("group");
provide<Ref<string>>("groupQuery", groupQuery);

const page = ref(1);
const size = ref(20);
const keyword = ref("");

const { links, isLoading, total, refetch } = useLinkFetch(page, size, keyword, groupQuery);

watch(
  () => groupQuery.value,
  () => {
    page.value = 1;
    selectedLinks.value.length = 0;
    checkedAll.value = false;
  }
);

const handleSelectPrevious = () => {
  if (!links.value) {
    return;
  }

  const currentIndex = links.value.findIndex((link) => link.metadata.name === selectedLink.value?.metadata.name);

  if (currentIndex > 0) {
    selectedLink.value = links.value[currentIndex - 1];
    return;
  }

  if (currentIndex <= 0) {
    selectedLink.value = undefined;
  }
};

const handleSelectNext = () => {
  if (!links.value) return;

  if (!selectedLink.value) {
    selectedLink.value = links.value[0];
    return;
  }
  const currentIndex = links.value.findIndex((link) => link.metadata.name === selectedLink.value?.metadata.name);
  if (currentIndex !== links.value.length - 1) {
    selectedLink.value = links.value[currentIndex + 1];
  }
};

const handleOpenCreateModal = (link: Link) => {
  selectedLink.value = link;
  editingModal.value = true;
};

const onPriorityChange = async () => {
  try {
    const promises = links.value?.map((link: Link, index) => {
      if (link.spec) {
        link.spec.priority = index;
      }
      return linksCoreApiClient.link.updateLink({
        name: link.metadata.name,
        link,
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

const onEditingModalClose = async () => {
  selectedLink.value = undefined;
  editingModal.value = false;
  refetch();
};

const handleDelete = (link: Link) => {
  Dialog.warning({
    title: "是否确认删除当前的链接？",
    description: "删除之后将无法恢复。",
    confirmType: "danger",
    onConfirm: async () => {
      try {
        await linksCoreApiClient.link.deleteLink({
          name: link.metadata.name,
        });

        Toast.success("删除成功");
      } catch (e) {
        console.error(e);
      } finally {
        queryClient.invalidateQueries({ queryKey: ["links"] });
      }
    },
  });
};

const handleDeleteInBatch = () => {
  Dialog.warning({
    title: "是否确认删除所选的链接？",
    description: "删除之后将无法恢复。",
    confirmType: "danger",
    onConfirm: async () => {
      try {
        const promises = selectedLinks.value.map((name) => {
          return linksCoreApiClient.link.deleteLink({ name });
        });

        if (promises) {
          await Promise.all(promises);
        }

        selectedLinks.value.length = 0;
        checkedAll.value = false;

        Toast.success("删除成功");
      } catch (e) {
        console.error(e);
      } finally {
        queryClient.invalidateQueries({ queryKey: ["links"] });
      }
    },
  });
};

const handleExportSelectedLinks = async () => {
  if (!links.value?.length) {
    return;
  }
  const yamlString = links.value
    .map((link) => {
      if (selectedLinks.value.includes(link.metadata.name)) {
        return yaml.stringify(link);
      }
    })
    .filter((link) => link)
    .join("---\n");
  const blob = new Blob([yamlString], { type: "text/yaml" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "links.yaml";
  link.click();
};

const handleImportFromYaml = async () => {
  const res = useFileSystemAccess({
    dataType: "Text",
    types: [
      {
        description: "yaml",
        accept: {
          "text/yaml": [".yaml", ".yml"],
        },
      },
    ],
    excludeAcceptAllOption: true,
  });

  await res.open();

  try {
    if (!res.data.value) {
      return;
    }

    const parsed = yaml.parseAllDocuments(res.data.value);
    if (Array.isArray(parsed)) {
      const promises = parsed.map((link: Link) => {
        return linksCoreApiClient.link.createLink({ link });
      });
      if (promises) {
        await Promise.all(promises);
      }
    } else {
      await linksCoreApiClient.link.createLink({ link: parsed });
    }
  } catch (e) {
    console.error(e);
  } finally {
    queryClient.invalidateQueries({ queryKey: ["links"] });
  }
};

const handleCheckAllChange = (e: Event) => {
  const { checked } = e.target as HTMLInputElement;
  checkedAll.value = checked;
  if (checkedAll.value) {
    selectedLinks.value =
      links.value?.map((link) => {
        return link.metadata.name;
      }) || [];
  } else {
    selectedLinks.value.length = 0;
  }
};

watch(selectedLinks, (newValue) => {
  checkedAll.value = newValue.length === links.value?.length;
});

// groups
const { groups } = useLinkGroupFetch();

function getGroup(groupName: string) {
  return groups.value?.find((group) => group.metadata.name === groupName);
}

async function handleMoveInBatch(group: LinkGroup) {
  const linksToUpdate = selectedLinks.value
    ?.map((name) => {
      return links.value?.find((link) => link.metadata.name === name);
    })
    .filter(Boolean) as Link[];

  const requests = linksToUpdate.map((link) => {
    return linksCoreApiClient.link.patchLink({
      name: link.metadata.name,
      jsonPatchInner: [
        {
          op: "add",
          path: "/spec/groupName",
          value: group.metadata.name || "",
        },
      ],
    });
  });

  if (requests) await Promise.all(requests);

  refetch();

  selectedLinks.value.length = 0;
  checkedAll.value = false;

  Toast.success("移动成功");
}

async function handleMove(link: Link, group: LinkGroup) {
  await linksCoreApiClient.link.patchLink({
    name: link.metadata.name,
    jsonPatchInner: [
      {
        op: "add",
        path: "/spec/groupName",
        value: group.metadata.name || "",
      },
    ],
  });

  Toast.success("移动成功");

  refetch();
}
</script>
<template>
  <LinkEditingModal v-if="editingModal" :link="selectedLink" @close="onEditingModalClose">
    <template #append-actions>
      <span @click="handleSelectPrevious">
        <IconArrowLeft />
      </span>
      <span @click="handleSelectNext">
        <IconArrowRight />
      </span>
    </template>
  </LinkEditingModal>
  <VPageHeader title="链接">
    <template #icon>
      <RiLinksLine />
    </template>
    <template #actions>
      <VSpace v-permission="['plugin:links:manage']">
        <VButton size="sm" type="default" @click="handleImportFromYaml"> 导入 </VButton>
      </VSpace>
    </template>
  </VPageHeader>
  <div class=":uno: p-4">
    <div class=":uno: flex flex-row gap-2">
      <div class=":uno: w-96">
        <GroupList />
      </div>
      <div class=":uno: flex-1">
        <VCard :body-class="[':uno: !p-0']">
          <template #header>
            <div class=":uno: block w-full bg-gray-50 px-4 py-3">
              <div class=":uno: relative flex flex-col items-start sm:flex-row sm:items-center">
                <div class=":uno: mr-4 hidden items-center sm:flex">
                  <input v-model="checkedAll" type="checkbox" @change="handleCheckAllChange" />
                </div>
                <div class=":uno: w-full flex flex-1 sm:w-auto">
                  <SearchInput v-if="!selectedLinks.length" v-model="keyword" />
                  <VSpace v-else>
                    <VButton type="danger" @click="handleDeleteInBatch"> 删除 </VButton>
                    <VDropdown>
                      <VButton type="default">更多</VButton>
                      <template #popper>
                        <VDropdownItem @click="handleExportSelectedLinks"> 导出 </VDropdownItem>
                        <VDropdownDivider />
                        <VDropdown placement="right" :triggers="['click']">
                          <VDropdownItem> 移动 </VDropdownItem>
                          <template #popper>
                            <template v-for="group in groups" :key="group.metadata.name">
                              <VDropdownItem
                                v-if="group.metadata.name !== groupQuery"
                                v-close-popper.all
                                @click="handleMoveInBatch(group)"
                              >
                                {{ group.spec?.displayName }}
                              </VDropdownItem>
                            </template>
                          </template>
                        </VDropdown>
                      </template>
                    </VDropdown>
                  </VSpace>
                </div>
                <div v-permission="['plugin:links:manage']" class=":uno: mt-4 flex sm:mt-0">
                  <VButton size="xs" @click="editingModal = true"> 新建 </VButton>
                </div>
              </div>
            </div>
          </template>
          <VLoading v-if="isLoading" />
          <Transition v-else-if="!links?.length" appear name="fade">
            <VEmpty message="你可以尝试刷新或者新建链接" title="当前没有链接">
              <template #actions>
                <VSpace>
                  <VButton @click="refetch"> 刷新</VButton>
                  <VButton v-permission="['system:menus:manage']" type="primary" @click="editingModal = true">
                    <template #icon>
                      <IconAddCircle class=":uno: size-full" />
                    </template>
                    新建
                  </VButton>
                </VSpace>
              </template>
            </VEmpty>
          </Transition>
          <Transition v-else appear name="fade">
            <div class=":uno: w-full overflow-x-auto">
              <table class=":uno: w-full border-spacing-0">
                <VueDraggable
                  v-model="links"
                  class=":uno: divide-y divide-gray-100"
                  group="link"
                  handle=".drag-element"
                  item-key="id"
                  tag="tbody"
                  @end="drag = false"
                  @start="drag = true"
                  @update="onPriorityChange"
                >
                  <VEntity
                    v-for="link in links"
                    :key="link.metadata.name"
                    :is-selected="selectedLinks.includes(link.metadata.name)"
                    class=":uno: group"
                  >
                    <template v-if="!keyword && groupQuery" #prepend>
                      <div
                        class=":uno: drag-element absolute inset-y-0 left-0 hidden w-3.5 cursor-move items-center bg-gray-100 transition-all group-hover:flex hover:bg-gray-200"
                      >
                        <IconList class=":uno: h-3.5 w-3.5" />
                      </div>
                    </template>

                    <template #checkbox>
                      <input v-model="selectedLinks" :value="link.metadata.name" type="checkbox" />
                    </template>

                    <template #start>
                      <VEntityField>
                        <template #description>
                          <VAvatar
                            :key="link.metadata.name"
                            :alt="link.spec?.displayName"
                            :src="link.spec?.logo"
                            size="md"
                          ></VAvatar>
                        </template>
                      </VEntityField>
                      <VEntityField :title="link.spec?.displayName">
                        <template #description>
                          <a
                            :href="link.spec?.url"
                            class=":uno: truncate text-xs text-gray-500 hover:text-gray-900"
                            target="_blank"
                          >
                            {{ link.spec?.url }}
                          </a>
                        </template>
                      </VEntityField>
                    </template>

                    <template #end>
                      <VEntityField
                        v-if="getGroup(link.spec?.groupName || '')"
                        :description="getGroup(link.spec?.groupName || '')?.spec?.displayName"
                      />
                      <VEntityField v-if="link.metadata.deletionTimestamp">
                        <template #description>
                          <VStatusDot v-tooltip="`删除中`" state="warning" animate />
                        </template>
                      </VEntityField>
                      <VEntityField :description="formatDatetime(link.metadata.creationTimestamp)" />
                    </template>
                    <template #dropdownItems>
                      <VDropdownItem @click="handleOpenCreateModal(link)"> 编辑 </VDropdownItem>
                      <VDropdown v-if="groups?.length" placement="left" :triggers="['click']">
                        <VDropdownItem> 移动 </VDropdownItem>
                        <template #popper>
                          <template v-for="group in groups" :key="group.metadata.name">
                            <VDropdownItem
                              v-if="group.metadata.name !== groupQuery"
                              v-close-popper.all
                              @click="handleMove(link, group)"
                            >
                              {{ group.spec?.displayName }}
                            </VDropdownItem>
                          </template>
                        </template>
                      </VDropdown>
                      <VDropdownItem type="danger" @click="handleDelete(link)"> 删除 </VDropdownItem>
                    </template>
                  </VEntity>
                </VueDraggable>
              </table>
            </div>
          </Transition>

          <template #footer>
            <VPagination v-model:page="page" v-model:size="size" :total="total" :size-options="[20, 30, 50, 100]" />
          </template>
        </VCard>
      </div>
    </div>
  </div>
</template>
