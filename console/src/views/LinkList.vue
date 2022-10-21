<script lang="ts" setup>
import { ref, watch } from "vue";
import Draggable from "vuedraggable";
import {
  IconList,
  IconArrowLeft,
  IconArrowRight,
  VButton,
  VCard,
  VPageHeader,
  VPagination,
  VSpace,
  VEntity,
  VEntityField,
  VAvatar,
  VStatusDot,
  Dialog,
} from "@halo-dev/components";
import GroupList from "../components/GroupList.vue";
import LinkEditingModal from "../components/LinkEditingModal.vue";
import apiClient from "@/utils/api-client";
import type { Link, LinkGroup, LinkList } from "@/types";
import yaml from "yaml";
import { useFileSystemAccess } from "@vueuse/core";
import cloneDeep from "lodash.clonedeep";
import { formatDatetime } from "@/utils/date";

const drag = ref(false);
const links = ref<Link[]>([] as Link[]);
const selectedLink = ref<Link | undefined>();
const selectedLinks = ref<string[]>([]);
const selectedGroup = ref<LinkGroup>();
const editingModal = ref(false);
const batchSaving = ref(false);
const checkedAll = ref(false);
const groupListRef = ref();

const handleFetchLinks = async () => {
  try {
    if (!selectedGroup.value?.spec?.links) {
      return;
    }

    const { data } = await apiClient.get<LinkList>(
      "/apis/core.halo.run/v1alpha1/links",
      {
        params: {
          fieldSelector: `name=(${selectedGroup.value.spec.links.join(",")})`,
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

const handleSelectPrevious = () => {
  const currentIndex = links.value.findIndex(
    (link) => link.metadata.name === selectedLink.value?.metadata.name
  );

  if (currentIndex > 0) {
    selectedLink.value = links.value[currentIndex - 1];
    return;
  }

  if (currentIndex <= 0) {
    selectedLink.value = undefined;
  }
};

const handleSelectNext = () => {
  if (!selectedLink.value) {
    selectedLink.value = links.value[0];
    return;
  }
  const currentIndex = links.value.findIndex(
    (link) => link.metadata.name === selectedLink.value?.metadata.name
  );
  if (currentIndex !== links.value.length - 1) {
    selectedLink.value = links.value[currentIndex + 1];
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
  Dialog.warning({
    title: "是否确认删除当前的链接？",
    description: "删除之后将无法恢复。",
    confirmType: "danger",
    onConfirm: async () => {
      try {
        apiClient.delete(
          `/apis/core.halo.run/v1alpha1/links/${link.metadata.name}`
        );
      } catch (e) {
        console.error(e);
      } finally {
        handleFetchLinks();
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
    if (!res.data.value) {
      return;
    }

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
  <VPageHeader title="链接">
    <template #actions>
      <VSpace v-permission="['plugin:links:manage']">
        <VButton size="sm" type="default" @click="handleImportFromYaml">
          导入
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
                  <VButton size="xs" @click="editingModal = true">
                    新增
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
            @change="handleSaveInBatch"
          >
            <template #item="{ element: link }">
              <li>
                <VEntity
                  :is-selected="selectedLinks.includes(link.metadata.name)"
                  class="links-group"
                >
                  <template #prepend>
                    <div
                      class="drag-element links-absolute links-inset-y-0 links-left-0 links-hidden links-w-3.5 links-cursor-move links-items-center links-bg-gray-100 links-transition-all hover:links-bg-gray-200 group-hover:links-flex"
                    >
                      <IconList class="h-3.5 w-3.5" />
                    </div>
                  </template>

                  <template #checkbox>
                    <input
                      v-model="selectedLinks"
                      :value="link.metadata.name"
                      class="links-h-4 links-w-4 links-rounded links-border-gray-300 links-text-indigo-600"
                      name="post-checkbox"
                      type="checkbox"
                    />
                  </template>

                  <template #start>
                    <VEntityField>
                      <template #description>
                        <VAvatar
                          :alt="link.spec.displayName"
                          :src="link.spec.logo"
                          size="md"
                        ></VAvatar>
                      </template>
                    </VEntityField>
                    <VEntityField
                      :title="link.spec.displayName"
                      :description="link.spec.description"
                    />
                  </template>

                  <template #end>
                    <VEntityField v-if="link.metadata.deletionTimestamp">
                      <template #description>
                        <VStatusDot
                          v-tooltip="`删除中`"
                          state="warning"
                          animate
                        />
                      </template>
                    </VEntityField>
                    <VEntityField
                      :description="
                        formatDatetime(link.metadata.creationTimestamp)
                      "
                    />
                  </template>
                  <template #dropdownItems>
                    <VButton
                      v-close-popper
                      block
                      type="secondary"
                      @click="handleOpenCreateModal(link)"
                    >
                      编辑
                    </VButton>
                    <VButton
                      v-close-popper
                      block
                      type="danger"
                      @click="handleDelete(link)"
                    >
                      删除
                    </VButton>
                  </template>
                </VEntity>
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
