<script lang="ts" setup name="LinkList">
import { onMounted, ref } from "vue";
import Draggable from "vuedraggable";
import {
  IconAddCircle,
  IconDeleteBin,
  VButton,
  VCard,
  VPageHeader,
  VSpace,
} from "@halo-dev/components";
import LinkCreationModal from "../components/LinkCreationModal.vue";
import axiosInstance from "@/utils/api-client";
import type { Link, LinkGroup } from "@/types/extension";

const drag = ref(false);
const links = ref<Link[]>();
const groups = ref<LinkGroup[]>();
const selectedLink = ref<Link | null>(null);
const createModal = ref(false);
const batchSaving = ref(false);

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
        <VButton type="secondary" @click="createModal = true">
          <template #icon>
            <IconAddCircle class="h-full w-full" />
          </template>
          添加链接
        </VButton>
      </VSpace>
    </template>
  </VPageHeader>
  <div class="p-4">
    <div class="flex flex-row gap-2">
      <div class="w-80">
        <VCard title="分组">
          <Draggable
            v-model="groups"
            group="group"
            item-key="id"
            tag="ul"
            @change="handleSaveGroupInBatch"
          >
            <template #item="{ element }">
              <li>
                {{ element.spec?.displayName }}
              </li>
            </template>
          </Draggable>
        </VCard>
      </div>
      <div class="flex-1">
        <VCard title="默认">
          <template #actions>
            <VSpace>
              <VButton
                size="sm"
                :loading="batchSaving"
                @click="handleSaveInBatch"
              >
                保存
              </VButton>
            </VSpace>
          </template>
          <Draggable
            v-model="links"
            group="people"
            @start="drag = true"
            @end="drag = false"
            item-key="id"
            class="links-container"
          >
            <template #item="{ element }">
              <div
                @click.stop="handleOpenCreateModal(element)"
                class="link-item"
              >
                <div class="link-avatar-container">
                  <img
                    :src="element.spec.logo"
                    :alt="element.spec.displayName"
                  />
                </div>
                <div class="link-metas">
                  <div>
                    <p class="link-name">{{ element.spec.displayName }}</p>
                    <p class="link-description">
                      {{ element.spec.description }}
                    </p>
                  </div>
                </div>
                <div class="absolute right-1 top-1">
                  <IconDeleteBin @click.stop="handleDelete(element)" />
                </div>
              </div>
            </template>
          </Draggable>
        </VCard>
      </div>
    </div>
  </div>
</template>
<style lang="scss" scoped>
.links-container {
  @apply grid grid-cols-1 gap-4 sm:grid-cols-2 md:grid-cols-4 lg:grid-cols-6 xl:grid-cols-8;

  .link-item {
    @apply relative rounded border border-gray-300 bg-white px-5 py-4 shadow-sm hover:shadow flex items-center space-x-3 hover:border-gray-400 cursor-pointer;
  }

  .link-avatar-container {
    @apply flex-shrink-0;

    img {
      @apply h-12 w-12 rounded-full;
    }
  }

  .link-metas {
    @apply flex-1 min-w-0;

    .link-name {
      @apply text-sm text-gray-500 truncate;
    }

    .link-description {
      @apply text-sm text-gray-500 truncate;
    }
  }
}
</style>
