<script lang="ts" setup name="LinkList">
import { ref, onMounted, computed } from "vue";
import {
  VPageHeader,
  VButton,
  VSpace,
  VCard,
  VModal,
  VInput,
  VTextarea,
  IconAddCircle,
  IconDeleteBin,
} from "@halo-dev/components";
import axiosInstance from "@/utils/api-client";
import type { Link } from "@/types/extension";

interface createFormState {
  link: Link;
  saving: boolean;
}

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
    links.value = data;
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
      <div class="links-container">
        <div
          @click.stop="handleOpenCreateModal(link)"
          v-for="(link, i) in links"
          :key="i"
          class="link-item"
        >
          <div class="link-avatar-container">
            <img :src="link.spec.logo" :alt="link.spec.displayName" />
          </div>
          <div class="link-metas">
            <div>
              <p class="link-name">{{ link.spec.displayName }}</p>
              <p class="link-description">{{ link.spec.description }}</p>
            </div>
          </div>
          <div class="absolute right-1 top-1">
            <IconDeleteBin @click.stop="handleDelete(link)" />
          </div>
        </div>
      </div>
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
      <VButton :loading="createForm.saving" @click="handleCreateLink">
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
