<script lang="ts" setup>
import LinksCard from "@/components/LinksCard.vue";
import { runLinkVerification } from "@/composables/link-verification";
import { useLinksFetch } from "@/composables/use-link-fetch";
import { IconExternalLinkLine, VButton, VLoading, VPageHeader, VSpace } from "@halo-dev/components";
import { useQueryClient } from "@tanstack/vue-query";
import { defineAsyncComponent, ref, shallowRef } from "vue";
import RiLinksLine from "~icons/ri/links-line";
import RiPulseLine from "~icons/ri/pulse-line";

const GroupCreationModal = defineAsyncComponent(
  () => import(/* webpackChunkName: "group-creation-modal" */ "@/components/GroupCreationModal.vue"),
);
const GroupSortModal = defineAsyncComponent(
  () => import(/* webpackChunkName: "group-sort-modal" */ "@/components/GroupSortModal.vue"),
);
const LinkImportModal = defineAsyncComponent(
  () => import(/* webpackChunkName: "link-import-modal" */ "@/components/LinkImportModal.vue"),
);

const { data, isLoading } = useLinksFetch();
const queryClient = useQueryClient();

const handleRouteToFront = () => {
  window.open("/links", "_blank");
};

const groupCreationModalVisible = ref(false);
const groupSortModalVisible = ref(false);
const linkImportModalVisible = ref(false);
const isVerifyingAllLinks = shallowRef(false);

async function handleVerifyAllLinks() {
  if (isVerifyingAllLinks.value) {
    return;
  }

  isVerifyingAllLinks.value = true;
  try {
    await runLinkVerification({
      queryClient,
      showSuccess: true,
    });
  } finally {
    isVerifyingAllLinks.value = false;
  }
}
</script>
<template>
  <VPageHeader title="链接">
    <template #icon>
      <RiLinksLine />
    </template>
    <template #actions>
      <VSpace>
        <VButton size="sm" :loading="isVerifyingAllLinks" @click="handleVerifyAllLinks">
          <template #icon>
            <RiPulseLine />
          </template>
          检测全部
        </VButton>
        <VButton size="sm" @click="linkImportModalVisible = true">批量导入</VButton>
        <VButton size="sm" @click="groupCreationModalVisible = true">新建分组</VButton>
        <VButton size="sm" @click="groupSortModalVisible = true">调整排序</VButton>
        <VButton @click="handleRouteToFront" size="sm" ghost>
          <template #icon>
            <IconExternalLinkLine />
          </template>
          跳转到前台
        </VButton>
      </VSpace>
    </template>
  </VPageHeader>
  <div class=":uno: p-4">
    <VLoading v-if="isLoading" />

    <div class=":uno: space-y-4" v-else>
      <LinksCard
        v-for="groupWithLinks in data"
        :group-with-links="groupWithLinks"
        :key="groupWithLinks.group?.metadata.name"
      >
      </LinksCard>
    </div>
  </div>

  <GroupCreationModal v-if="groupCreationModalVisible" @close="groupCreationModalVisible = false" />
  <GroupSortModal v-if="groupSortModalVisible" @close="groupSortModalVisible = false" />
  <LinkImportModal v-if="linkImportModalVisible" @close="linkImportModalVisible = false" />
</template>
