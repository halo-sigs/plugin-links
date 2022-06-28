<script lang="ts" setup name="LinkList">
import { onMounted, ref, watch } from "vue";
import type { Ref } from "vue";
import Draggable from "vuedraggable";
import {
  IconAddCircle,
  IconList,
  IconSettings,
  VButton,
  VCard,
  VInput,
  VPageHeader,
  VSpace,
} from "@halo-dev/components";
import LinkCreationModal from "../components/LinkCreationModal.vue";
import { axiosInstance } from "@halo-dev/admin-shared";
import type { Link, LinkGroup } from "@/types/extension";
import yaml from "yaml";

const drag = ref(false);
const links = ref<Link[]>();
const groups = ref<LinkGroup[]>();
const selectedLink = ref<Link | null>(null);
const selectedLinks = ref<string[]>([]);
const selectedGroup = ref<LinkGroup | null>(null);
const createModal = ref(false);
const batchSaving = ref(false);
const checkedAll = ref(false);

const handleFetchLinks = async () => {
  selectedLink.value = null;

  try {
    const { data } = await axiosInstance.get<Link[]>(
      `/apis/core.halo.run/v1alpha1/links`
    );
    // sort by priority

    links.value = data
      .map((link) => {
        if (link.spec) {
          link.spec.priority = link.spec.priority || 0;
        }
        return link;
      })
      .sort((a, b) => {
        return (a.spec?.priority || 0) - (b.spec?.priority || 0);
      });
  } catch (e) {
    console.error(e);
  }
};

const handleFetchLinkGroups = async () => {
  try {
    const { data } = await axiosInstance.get<LinkGroup[]>(
      `/apis/core.halo.run/v1alpha1/linkgroups`
    );
    groups.value = data
      .map((group) => {
        if (group.spec) {
          group.spec.priority = group.spec.priority || 0;
        }
        return group;
      })
      .sort((a, b) => {
        return (a.spec?.priority || 0) - (b.spec?.priority || 0);
      });

    // set default selected group
    if (groups.value?.length) {
      selectedGroup.value = groups.value[0];
    }
  } catch (e) {
    console.error(e);
  }
};

const handleOpenCreateModal = (link: Link) => {
  selectedLink.value = link;
  createModal.value = true;
};

const handleSaveInBatch = async () => {
  try {
    batchSaving.value = true;
    const promises = links.value?.map((link: Link, index) => {
      if (link.spec) {
        link.spec.priority = index;
      }
      return axiosInstance.put<Link>(
        `/apis/core.halo.run/v1alpha1/links/${link.metadata.name}`,
        link
      );
    });
    if (promises) {
      await Promise.all(promises);
    }
  } catch (e) {
    console.error(e);
  } finally {
    await handleFetchLinks();
    batchSaving.value = false;
  }
};

const handleSaveGroupInBatch = async () => {
  try {
    const promises = groups.value?.map((group: LinkGroup, index) => {
      if (group.spec) {
        group.spec.priority = index;
      }
      return axiosInstance.put<LinkGroup>(
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
    await handleFetchLinkGroups();
  }
};

const handleDelete = (link: Link) => {
  try {
    axiosInstance.delete(
      `/apis/core.halo.run/v1alpha1/links/${link.metadata.name}`
    );
  } catch (e) {
    console.error(e);
  } finally {
    handleFetchLinks();
  }
};

const handleDeleteInBatch = async () => {
  try {
    const promises = selectedLinks.value.map((link) => {
      return axiosInstance.delete(`/apis/core.halo.run/v1alpha1/links/${link}`);
    });
    if (promises) {
      await Promise.all(promises);
    }
  } catch (e) {
    console.error(e);
  } finally {
    await handleFetchLinks();
  }
};

const handleGetLinksByGroup = (group: LinkGroup) => {
  return links.value?.filter((link) => {
    return link.spec?.groupName === group.metadata.name;
  });
};

const handleExportSelectedLinks = async () => {
  if (!links.value?.length) {
    return;
  }
  const yamlString = links.value
    ?.map((link) => {
      return yaml.stringify(link);
    })
    .join("---\n");
  const blob = new Blob([yamlString], { type: "text/yaml" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "links.yaml";
  link.click();
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

onMounted(() => {
  handleFetchLinks();
  handleFetchLinkGroups();
});
</script>
<template>
  <LinkCreationModal
    v-model:visible="createModal"
    :link="selectedLink"
    @close="handleFetchLinks"
  />
  <VPageHeader title="友情链接">
    <template #actions>
      <VSpace>
        <VButton size="sm" type="default" @click="createModal = true">
          导入
        </VButton>
        <VButton type="secondary" @click="createModal = true">
          <template #icon>
            <IconAddCircle class="h-full w-full" />
          </template>
          新增链接
        </VButton>
      </VSpace>
    </template>
  </VPageHeader>
  <div class="p-4">
    <div class="flex flex-row gap-2">
      <div class="w-80">
        <VCard title="分组" :bodyClass="['!p-0']">
          <Draggable
            v-model="groups"
            group="group"
            item-key="metadata.name"
            tag="div"
            @change="handleSaveGroupInBatch"
            class="divide-y divide-gray-100 bg-white"
          >
            <template #item="{ element }">
              <div
                class="relative flex items-center p-4"
                :class="{
                  'bg-gray-50':
                    selectedGroup?.metadata.name === element.metadata.name,
                }"
                @click="selectedGroup = element"
              >
                <div>
                  <IconList class="drag-element cursor-move" />
                </div>
                <span class="ml-3 flex flex-1 cursor-pointer flex-col">
                  <span class="block text-sm font-medium">
                    {{ element.spec?.displayName }}
                  </span>
                  <span class="block text-sm text-gray-400">
                    {{ handleGetLinksByGroup(element).length }} 个
                  </span>
                </span>
                <div class="self-center">
                  <IconSettings
                    class="cursor-pointer transition-all hover:text-blue-600"
                  />
                </div>
              </div>
            </template>
          </Draggable>

          <template #footer>
            <VButton block type="secondary">新增分组</VButton>
          </template>
        </VCard>
      </div>
      <div class="flex-1">
        <VCard :body-class="['!p-0']">
          <template #header>
            <div class="block w-full bg-gray-50 px-4 py-3">
              <div
                class="relative flex flex-col items-start sm:flex-row sm:items-center"
              >
                <div class="mr-4 hidden items-center sm:flex">
                  <input
                    v-model="checkedAll"
                    class="h-4 w-4 rounded border-gray-300 text-indigo-600"
                    type="checkbox"
                    @change="handleCheckAllChange"
                  />
                </div>
                <div class="flex w-full flex-1 sm:w-auto">
                  <VInput
                    v-if="!selectedLinks.length"
                    class="w-72"
                    placeholder="输入关键词搜索"
                  />
                  <VSpace v-else>
                    <VButton type="danger" @click="handleDeleteInBatch">
                      删除
                    </VButton>
                    <FloatingDropdown>
                      <VButton type="default">更多</VButton>
                      <template #popper>
                        <div class="w-48 p-2">
                          <VSpace direction="column" class="w-full">
                            <VButton @click="handleExportSelectedLinks" block>
                              导出
                            </VButton>
                          </VSpace>
                        </div>
                      </template>
                    </FloatingDropdown>
                  </VSpace>
                </div>
                <div class="mt-4 flex sm:mt-0">
                  <VButton
                    size="sm"
                    :loading="batchSaving"
                    @click="handleSaveInBatch"
                  >
                    保存排序
                  </VButton>
                </div>
              </div>
            </div>
          </template>
          <Draggable
            v-model="links"
            group="link"
            @start="drag = true"
            @end="drag = false"
            item-key="id"
            class="box-border h-full w-full divide-y divide-gray-100"
            tag="ul"
            handle=".drag-element"
          >
            <template #item="{ element: link }">
              <li>
                <div
                  :class="{
                    'bg-gray-100': selectedLinks.includes(link.metadata.name),
                  }"
                  class="relative block px-4 py-3 transition-all hover:bg-gray-50"
                >
                  <div
                    v-show="selectedLinks.includes(link.metadata.name)"
                    class="bg-themeable-primary absolute inset-y-0 left-0 w-0.5"
                  ></div>
                  <div class="relative flex flex-row items-center">
                    <div class="mr-4 hidden items-center sm:flex">
                      <input
                        v-model="selectedLinks"
                        name="link-checkbox"
                        class="h-4 w-4 cursor-pointer rounded border-gray-300 text-indigo-600"
                        type="checkbox"
                        :value="link.metadata.name"
                      />
                    </div>
                    <div v-if="link.spec.logo" class="mr-4">
                      <div
                        class="h-12 w-12 overflow-hidden rounded border bg-white hover:shadow-sm"
                      >
                        <img
                          :alt="link.metadata.name"
                          :src="link.spec.logo"
                          class="h-full w-full"
                        />
                      </div>
                    </div>
                    <div class="flex-1">
                      <div class="flex flex-row items-center">
                        <div class="drag-element mr-2 cursor-move">
                          <IconList class="h-4 w-4" />
                        </div>
                        <span
                          class="truncate text-sm font-medium text-gray-900"
                        >
                          {{ link.spec.displayName }}
                        </span>
                      </div>
                      <div class="mt-2 flex">
                        <VSpace align="start" direction="column" spacing="xs">
                          <span class="text-xs text-gray-500">
                            {{ link.spec.description }}
                          </span>
                        </VSpace>
                      </div>
                    </div>
                    <div class="flex">
                      <div
                        class="inline-flex flex-col flex-col-reverse items-end gap-4 sm:flex-row sm:items-center sm:gap-6"
                      >
                        <time
                          class="text-sm text-gray-500"
                          datetime="2020-01-07"
                        >
                          {{ link.metadata.creationTimestamp }}
                        </time>

                        <span class="cursor-pointer">
                          <IconSettings
                            @click.stop="handleOpenCreateModal(link)"
                          />
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              </li>
            </template>
          </Draggable>

          <template #footer>
            <div class="flex items-center justify-end bg-white">
              <div class="flex flex-1 items-center justify-end">
                <div>
                  <nav
                    aria-label="Pagination"
                    class="relative z-0 inline-flex -space-x-px rounded-md shadow-sm"
                  >
                    <a
                      class="relative inline-flex items-center rounded-l-md border border-gray-300 bg-white px-2 py-2 text-sm font-medium text-gray-500 hover:bg-gray-50"
                      href="#"
                    >
                      <span class="sr-only">Previous</span>
                      <svg
                        aria-hidden="true"
                        class="h-5 w-5"
                        fill="currentColor"
                        viewBox="0 0 20 20"
                        xmlns="http://www.w3.org/2000/svg"
                      >
                        <path
                          clip-rule="evenodd"
                          d="M12.707 5.293a1 1 0 010 1.414L9.414 10l3.293 3.293a1 1 0 01-1.414 1.414l-4-4a1 1 0 010-1.414l4-4a1 1 0 011.414 0z"
                          fill-rule="evenodd"
                        />
                      </svg>
                    </a>
                    <a
                      aria-current="page"
                      class="relative z-10 inline-flex items-center border border-indigo-500 bg-indigo-50 px-4 py-2 text-sm font-medium text-indigo-600"
                      href="#"
                    >
                      1
                    </a>
                    <a
                      class="relative inline-flex items-center border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-500 hover:bg-gray-50"
                      href="#"
                    >
                      2
                    </a>
                    <span
                      class="relative inline-flex items-center border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700"
                    >
                      ...
                    </span>
                    <a
                      class="relative hidden items-center border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-500 hover:bg-gray-50 md:inline-flex"
                      href="#"
                    >
                      4
                    </a>
                    <a
                      class="relative inline-flex items-center rounded-r-md border border-gray-300 bg-white px-2 py-2 text-sm font-medium text-gray-500 hover:bg-gray-50"
                      href="#"
                    >
                      <span class="sr-only">Next</span>
                      <svg
                        aria-hidden="true"
                        class="h-5 w-5"
                        fill="currentColor"
                        viewBox="0 0 20 20"
                        xmlns="http://www.w3.org/2000/svg"
                      >
                        <path
                          clip-rule="evenodd"
                          d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z"
                          fill-rule="evenodd"
                        />
                      </svg>
                    </a>
                  </nav>
                </div>
              </div>
            </div>
          </template>
        </VCard>
      </div>
    </div>
  </div>
</template>
<style lang="scss" scoped></style>
