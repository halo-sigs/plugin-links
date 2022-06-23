<script lang="ts">
import { defineComponent, ref, onMounted } from "vue";
import {
  VPageHeader,
  VButton,
  VSpace,
  VCard,
  IconAddCircle,
} from "@halo-dev/components";
import axiosInstance from "@/utils/api-client";
import type { Link } from "@/types/extension";

export default defineComponent({
  name: "LinkList",
  components: {
    VPageHeader,
    VButton,
    VSpace,
    VCard,
    IconAddCircle,
  },
  setup() {
    const links = ref<Link[]>();

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

    onMounted(handleFetchLinks);

    return {
      links,
    };
  },
});
</script>
<template>
  <VPageHeader title="友情链接">
    <template #actions>
      <VSpace>
        <VButton type="secondary">
          <template #icon>
            <IconAddCircle class="h-full w-full" />
          </template>
          添加链接
        </VButton>
      </VSpace>
    </template>
  </VPageHeader>
  <div>
    <VCard title="默认">
      <div class="links-container">
        <div v-for="(link, i) in links" :key="i" class="link-item">
          <div class="link-avatar-container">
            <img :src="link.spec.logo" :alt="link.spec.displayName" />
          </div>
          <div class="link-metas">
            <div>
              <p class="link-name">{{ link.spec.displayName }}</p>
              <p class="link-description">{{ link.spec.description }}</p>
            </div>
          </div>
        </div>
      </div>
    </VCard>
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
