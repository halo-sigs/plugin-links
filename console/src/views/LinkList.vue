<script lang="ts" setup>
import LinksCard from "@/components/LinksCard.vue";
import { useLinkGroupFetch } from "@/composables/use-group-fetch";
import { IconExternalLinkLine, VButton, VLoading, VPageHeader, VSpace } from "@halo-dev/components";
import { defineAsyncComponent, ref } from "vue";
import RiLinksLine from "~icons/ri/links-line";

const GroupCreationModal = defineAsyncComponent(() => import(/* webpackChunkName: "group-creation-modal" */ "@/components/GroupCreationModal.vue"));
const GroupSortModal = defineAsyncComponent(() => import(/* webpackChunkName: "group-sort-modal" */ "@/components/GroupSortModal.vue"));

const { data: groups, isLoading } = useLinkGroupFetch();

const handleRouteToFront = () => {
  window.open("/links", "_blank");
};

const groupCreationModalVisible = ref(false);
const groupSortModalVisible = ref(false);
</script>
<template>
  <VPageHeader title="链接">
    <template #icon>
      <RiLinksLine />
    </template>
    <template #actions>
      <VSpace>
        <VButton size="sm" @click="groupCreationModalVisible = true">新建分组</VButton>
        <VButton size="sm" @click="groupSortModalVisible = true">调整排序</VButton>
        <VButton @click="handleRouteToFront" size="sm" ghost>
          <template #icon>
            <IconExternalLinkLine class=":uno: size-full" />
          </template>
          跳转到前台
        </VButton>
      </VSpace>
    </template>
  </VPageHeader>
  <div class=":uno: p-4">
    <VLoading v-if="isLoading" />

    <div class=":uno: space-y-4" v-else>
      <LinksCard v-for="group in groups" :group="group" :key="group.metadata.name"> </LinksCard>
      <LinksCard></LinksCard>
    </div>
  </div>

  <GroupCreationModal v-if="groupCreationModalVisible" @close="groupCreationModalVisible = false" />
  <GroupSortModal v-if="groupSortModalVisible" @close="groupSortModalVisible = false" />
</template>
