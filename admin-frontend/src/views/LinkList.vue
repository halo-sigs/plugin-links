<script lang="ts" setup name="LinkList">
import { computed, onMounted, ref } from "vue";
import draggable from "vuedraggable";
import {
  IconAddCircle,
  IconDeleteBin,
  IconSave,
  VButton,
  VCard,
  VInput,
  VModal,
  VPageHeader,
  VSpace,
  VTextarea,
} from "@halo-dev/components";
import axiosInstance from "@/utils/api-client";
import type { Link } from "@/types/extension";

interface createFormState {
  link: Link;
  saving: boolean;
}

const drag = ref(false);
const links = ref<Link[]>();
const createModal = ref(false);
const createForm = ref<createFormState>({
  link: {
    metadata: {
      name: Math.random().toString(),
    },
    spec: {
      displayName: "",
      url: "",
      logo: "",
    },
    kind: "Link",
    apiVersion: "core.halo.run/v1alpha1",
  },
  saving: false,
});
const batchSaving = ref(false);

const isUpdateMode = computed(() => {
  return !!createForm.value.link.metadata.creationTimestamp;
});

const createModalTitle = computed(() => {
  return isUpdateMode.value ? "编辑链接" : "添加链接";
});

const handleFetchLinks = async () => {
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

const handleOpenCreateModal = (link: Link) => {
  createForm.value.link = link;
  createModal.value = true;
};

const handleCreateLink = async () => {
  try {
    createForm.value.saving = true;
    if (isUpdateMode.value) {
      await axiosInstance.put<Link>(
        `/apis/core.halo.run/v1alpha1/links/${createForm.value.link.metadata.name}`,
        createForm.value.link
      );
    } else {
      await axiosInstance.post<Link>(
        `/apis/core.halo.run/v1alpha1/links`,
        createForm.value.link
      );
    }
    createModal.value = false;
  } catch (e) {
    console.error(e);
  } finally {
    createForm.value.saving = false;
    await handleFetchLinks();
  }
};

const handleSaveInBatch = () => {
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
      Promise.all(promises);
    }
  } catch (e) {
    console.error(e);
  } finally {
    batchSaving.value = false;
    handleFetchLinks();
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

onMounted(handleFetchLinks);
</script>
<template>
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
    <VCard title="默认">
      <template #actions>
        <VSpace>
          <VButton size="sm" :loading="batchSaving" @click="handleSaveInBatch"
            >保存
          </VButton>
        </VSpace>
      </template>
      <draggable
        v-model="links"
        group="people"
        @start="drag = true"
        @end="drag = false"
        item-key="id"
        class="links-container"
      >
        <template #item="{ element }">
          <div @click.stop="handleOpenCreateModal(element)" class="link-item">
            <div class="link-avatar-container">
              <img :src="element.spec.logo" :alt="element.spec.displayName" />
            </div>
            <div class="link-metas">
              <div>
                <p class="link-name">{{ element.spec.displayName }}</p>
                <p class="link-description">{{ element.spec.description }}</p>
              </div>
            </div>
            <div class="absolute right-1 top-1">
              <IconDeleteBin @click.stop="handleDelete(element)" />
            </div>
          </div>
        </template>
      </draggable>
    </VCard>
  </div>
  <VModal v-model:visible="createModal" :title="createModalTitle" :width="600">
    <form>
      <div class="space-y-6 divide-y-0 sm:divide-y sm:divide-gray-200">
        <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:pt-5">
          <label
            class="block text-sm font-medium text-gray-700 sm:mt-px sm:pt-2"
          >
            网站名称
          </label>
          <div class="mt-1 sm:col-span-2 sm:mt-0">
            <VInput v-model="createForm.link.spec.displayName"></VInput>
          </div>
        </div>

        <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:pt-5">
          <label
            class="block text-sm font-medium text-gray-700 sm:mt-px sm:pt-2"
          >
            网站地址
          </label>
          <div class="mt-1 sm:col-span-2 sm:mt-0">
            <VInput v-model="createForm.link.spec.url"></VInput>
          </div>
        </div>

        <div class="sm:grid sm:grid-cols-3 sm:items-center sm:gap-4 sm:pt-5">
          <label class="block text-sm font-medium text-gray-700"> Logo </label>
          <div class="mt-1 sm:col-span-2 sm:mt-0">
            <VInput v-model="createForm.link.spec.logo"></VInput>
          </div>
        </div>

        <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:pt-5">
          <label
            class="block text-sm font-medium text-gray-700 sm:mt-px sm:pt-2"
          >
            描述
          </label>
          <div class="mt-1 sm:col-span-2 sm:mt-0">
            <VTextarea v-model="createForm.link.spec.description"></VTextarea>
          </div>
        </div>
      </div>
    </form>
    <template #footer>
      <VButton
        type="secondary"
        :loading="createForm.saving"
        @click="handleCreateLink"
      >
        <template #icon>
          <IconSave class="w-full h-full" />
        </template>
        保存
      </VButton>
    </template>
  </VModal>
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
