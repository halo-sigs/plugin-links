<script lang="ts" name="LinkList" setup>
import { ref, watch } from "vue";
import Draggable from "vuedraggable";
import {
  IconAddCircle,
  IconInformation,
  IconList,
  IconSettings,
  useDialog,
  VButton,
  VCard,
  VPageHeader,
  VPagination,
  VSpace,
} from "@halo-dev/components";
import GroupList from "../components/GroupList.vue";
import LinkEditingModal from "../components/LinkEditingModal.vue";
import apiClient from "@/utils/api-client";
import type { Link, LinkGroup, LinkList } from "@/types";
import yaml from "yaml";
import { useFileSystemAccess } from "@vueuse/core";
import { UseImage } from "@vueuse/components";
import cloneDeep from "lodash.clonedeep";

const drag = ref(false);
const links = ref<Link[]>();
const selectedLink = ref<Link | null>(null);
const selectedLinks = ref<string[]>([]);
const selectedGroup = ref<LinkGroup | null>(null);
const editingModal = ref(false);
const batchSaving = ref(false);
const checkedAll = ref(false);
const groupListRef = ref();

const dialog = useDialog();

const handleFetchLinks = async () => {
  selectedLink.value = null;

  try {
    if (!selectedGroup.value?.spec?.links) {
      return;
    }

    const { data } = await apiClient.get<LinkList>(
      "/apis/core.halo.run/v1alpha1/links",
      {
        params: {
          fieldSelector: [`name=(${selectedGroup.value.spec.links.join(",")})`],
        },
      }
    );

    // sort by priority
    links.value = data.items
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
    console.error("Failed to fetch links", e);
  }
};

const handleOpenCreateModal = (link: Link) => {
  selectedLink.value = link;
  editingModal.value = true;
};

const handleSaveInBatch = async () => {
  try {
    batchSaving.value = true;
    const promises = links.value?.map((link: Link, index) => {
      if (link.spec) {
        link.spec.priority = index;
      }
      return apiClient.put(
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

const onLinkSaved = async (link: Link) => {
  const groupToUpdate = cloneDeep(selectedGroup.value);

  if (groupToUpdate) {
    groupToUpdate.spec.links.push(link.metadata.name);

    await apiClient.put(
      `/apis/core.halo.run/v1alpha1/linkgroups/${groupToUpdate.metadata.name}`,
      groupToUpdate
    );
  }

  await groupListRef.value.handleFetchGroups();
  await handleFetchLinks();
};

const handleDelete = (link: Link) => {
  try {
    apiClient.delete(
      `/apis/core.halo.run/v1alpha1/links/${link.metadata.name}`
    );
  } catch (e) {
    console.error(e);
  } finally {
    handleFetchLinks();
  }
};

const handleDeleteInBatch = () => {
  dialog.warning({
    title: "是否确认删除所选的链接？",
    description: "删除之后将无法恢复。",
    onConfirm: async () => {
      try {
        const promises = selectedLinks.value.map((link) => {
          return apiClient.delete(`/apis/core.halo.run/v1alpha1/links/${link}`);
        });
        if (promises) {
          await Promise.all(promises);
        }
      } catch (e) {
        console.error(e);
      } finally {
        await handleFetchLinks();
      }
    },
  });
};

const handleExportSelectedLinks = async () => {
  if (!links.value?.length) {
    return;
  }
  const yamlString = links.value
    ?.map((link) => {
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
    const parsed = yaml.parse(res.data.value);
    if (Array.isArray(parsed)) {
      const promises = parsed.map((link) => {
        return apiClient.post("/apis/core.halo.run/v1alpha1/links", link);
      });
      if (promises) {
        await Promise.all(promises);
      }
    } else {
      await apiClient.post("/apis/core.halo.run/v1alpha1/links", parsed);
    }
  } catch (e) {
    console.error(e);
  } finally {
    await handleFetchLinks();
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
</script>
<template>
  <LinkEditingModal
    v-model:visible="editingModal"
    :link="selectedLink"
    @close="handleFetchLinks"
    @saved="onLinkSaved"
  />
  <VPageHeader title="友情链接">
    <template #actions>
      <VSpace v-permission="['plugin:links:manage']">
        <VButton size="sm" type="default" @click="handleImportFromYaml">
          导入
        </VButton>
        <VButton type="secondary" @click="editingModal = true">
          <template #icon>
            <IconAddCircle class="links-h-full links-w-full" />
          </template>
          新增链接
        </VButton>
      </VSpace>
    </template>
  </VPageHeader>
  <div class="links-p-4">
    <div class="links-flex links-flex-row links-gap-2">
      <div class="links-w-80">
        <GroupList
          ref="groupListRef"
          v-model:selected-group="selectedGroup"
          @select="handleFetchLinks"
        />
      </div>
      <div class="links-flex-1">
        <VCard :body-class="['!p-0']">
          <template #header>
            <div
              class="links-block links-w-full links-bg-gray-50 links-px-4 links-py-3"
            >
              <div
                class="links-relative links-flex links-flex-col links-items-start sm:links-flex-row sm:links-items-center"
              >
                <div
                  class="links-mr-4 links-hidden links-items-center sm:links-flex"
                >
                  <input
                    v-model="checkedAll"
                    class="links-h-4 links-w-4 links-rounded links-border-gray-300 links-text-indigo-600"
                    type="checkbox"
                    @change="handleCheckAllChange"
                  />
                </div>
                <div
                  class="links-flex links-w-full links-flex-1 sm:links-w-auto"
                >
                  <FormKit
                    v-if="!selectedLinks.length"
                    placeholder="输入关键词搜索"
                    type="text"
                  ></FormKit>
                  <VSpace v-else>
                    <VButton type="danger" @click="handleDeleteInBatch">
                      删除
                    </VButton>
                    <FloatingDropdown>
                      <VButton type="default">更多</VButton>
                      <template #popper>
                        <div class="links-w-48 links-p-2">
                          <VSpace class="links-w-full" direction="column">
                            <VButton block @click="handleExportSelectedLinks">
                              导出
                            </VButton>
                          </VSpace>
                        </div>
                      </template>
                    </FloatingDropdown>
                  </VSpace>
                </div>
                <div
                  v-permission="['plugin:links:manage']"
                  class="links-mt-4 links-flex sm:links-mt-0"
                >
                  <VButton
                    :loading="batchSaving"
                    size="sm"
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
            class="links-box-border links-h-full links-w-full links-divide-y links-divide-gray-100"
            group="link"
            handle=".drag-element"
            item-key="id"
            tag="ul"
            @end="drag = false"
            @start="drag = true"
          >
            <template #item="{ element: link }">
              <li>
                <div
                  :class="{
                    'links-bg-gray-100': selectedLinks.includes(
                      link.metadata.name
                    ),
                  }"
                  class="links-relative links-block links-px-4 links-py-3 links-transition-all hover:links-bg-gray-50"
                >
                  <div
                    v-show="selectedLinks.includes(link.metadata.name)"
                    class="links-bg-themeable-primary links-absolute links-inset-y-0 links-left-0 links-w-0.5"
                  ></div>
                  <div
                    class="links-relative links-flex links-flex-row links-items-center"
                  >
                    <div
                      class="links-mr-4 links-hidden links-items-center sm:links-flex"
                    >
                      <input
                        v-model="selectedLinks"
                        :value="link.metadata.name"
                        class="links-h-4 links-w-4 links-cursor-pointer links-rounded links-border-gray-300 links-text-indigo-600"
                        name="link-checkbox"
                        type="checkbox"
                      />
                    </div>
                    <div v-if="link.spec.logo" class="links-mr-4">
                      <div
                        class="links-inline-flex links-h-12 links-w-12 links-items-center links-justify-center links-overflow-hidden links-rounded links-border links-bg-white hover:links-shadow-sm"
                      >
                        <UseImage :src="link.spec.logo">
                          <template #loading>
                            <svg
                              class="links-h-5 links-w-5 links-animate-spin"
                              fill="none"
                              viewBox="0 0 24 24"
                              xmlns="http://www.w3.org/2000/svg"
                            >
                              <circle
                                class="links-opacity-25"
                                cx="12"
                                cy="12"
                                r="10"
                                stroke="currentColor"
                                stroke-width="4"
                              ></circle>
                              <path
                                class="links-opacity-75"
                                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                                fill="currentColor"
                              ></path>
                            </svg>
                          </template>
                          <template #error>
                            <IconInformation class="links-h-5 links-w-5" />
                          </template>
                        </UseImage>
                      </div>
                    </div>
                    <div class="links-flex-1">
                      <div class="links-flex links-flex-row links-items-center">
                        <div class="drag-element links-mr-2 links-cursor-move">
                          <IconList class="links-h-4 links-w-4" />
                        </div>
                        <span
                          class="links-truncate links-text-sm links-font-medium links-text-gray-900"
                        >
                          {{ link.spec.displayName }}
                        </span>
                      </div>
                      <div class="links-mt-2 links-flex">
                        <VSpace align="start" direction="column" spacing="xs">
                          <span class="links-text-xs links-text-gray-500">
                            {{ link.spec.description }}
                          </span>
                        </VSpace>
                      </div>
                    </div>
                    <div class="links-flex">
                      <div
                        class="links-inline-flex links-flex-col links-flex-col-reverse links-items-end links-gap-4 sm:links-flex-row sm:links-items-center sm:links-gap-6"
                      >
                        <time
                          class="links-text-sm links-text-gray-500"
                          datetime="2020-01-07"
                        >
                          {{ link.metadata.creationTimestamp }}
                        </time>

                        <span
                          v-permission="['plugin:links:manage']"
                          class="links-cursor-pointer"
                        >
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
            <div
              class="links-flex links-items-center links-justify-end links-bg-white"
            >
              <div
                class="links-flex links-flex-1 links-items-center links-justify-end"
              >
                <VPagination :page="1" :size="10" :total="20" />
              </div>
            </div>
          </template>
        </VCard>
      </div>
    </div>
  </div>
</template>
<style lang="scss" scoped></style>
