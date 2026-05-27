<script lang="ts" setup>
import LinksCard from "@/components/LinksCard.vue";
import { runLinkVerification } from "@/composables/link-verification";
import {
  matchesLinkVerificationStatusFilter,
  type LinkVerificationStatusFilter,
} from "@/composables/link-verification-status";
import { useLinksFetch, type GroupWithLinks } from "@/composables/use-link-fetch";
import {
  Dialog,
  IconExternalLinkLine,
  IconRefreshLine,
  VButton,
  VLoading,
  VPageHeader,
  VSpace,
} from "@halo-dev/components";
import { useQueryClient } from "@tanstack/vue-query";
import { computed, defineAsyncComponent, ref, shallowRef } from "vue";
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

const { data, isLoading, isFetching, refetch } = useLinksFetch();
const queryClient = useQueryClient();

const handleRouteToFront = () => {
  window.open("/links", "_blank");
};

const groupCreationModalVisible = ref(false);
const groupSortModalVisible = ref(false);
const linkImportModalVisible = ref(false);
const isVerifyingAllLinks = shallowRef(false);
const selectedStatusFilter = shallowRef<LinkVerificationStatusFilter>("all");

const statusFilterOptions: Array<{ label: string; value: LinkVerificationStatusFilter }> = [
  { label: "全部", value: "all" },
  { label: "访问异常的链接", value: "access-error" },
  { label: "没有反链的链接", value: "backlink-missing" },
];

const filteredGroups = computed(() => filterGroupsByStatus(data.value || [], selectedStatusFilter.value));

function handleVerifyAllLinks() {
  if (isVerifyingAllLinks.value) {
    return;
  }

  Dialog.warning({
    title: "确认检测全部链接？",
    description: "系统会在后台按队列逐个检测所有链接。检测可能需要一定时间，请稍后刷新页面查看。确认继续吗？",
    confirmType: "primary",
    onConfirm: verifyAllLinks,
  });
}

async function verifyAllLinks() {
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

function filterGroupsByStatus(groups: GroupWithLinks[], filter: LinkVerificationStatusFilter) {
  if (filter === "all") {
    return groups;
  }

  return groups
    .map((groupWithLinks) => ({
      ...groupWithLinks,
      links: groupWithLinks.links.filter((link) => matchesLinkVerificationStatusFilter(link, filter)),
    }))
    .filter((groupWithLinks) => groupWithLinks.links.length > 0);
}
</script>
<template>
  <VPageHeader title="链接">
    <template #icon>
      <RiLinksLine />
    </template>
    <template #actions>
      <VButton @click="handleRouteToFront" size="sm" ghost>
        <template #icon>
          <IconExternalLinkLine />
        </template>
        跳转到前台
      </VButton>
    </template>
  </VPageHeader>
  <div class=":uno: p-4">
    <div
      class=":uno: mb-4 flex flex-col gap-3 border border-gray-200 rounded-lg bg-white/90 p-3 shadow-sm md:flex-row md:items-center md:justify-between"
    >
      <VSpace class=":uno: flex-wrap">
        <VButton size="sm" @click="groupCreationModalVisible = true">新建分组</VButton>
        <VButton size="sm" @click="groupSortModalVisible = true">调整排序</VButton>
        <VButton size="sm" @click="linkImportModalVisible = true">批量导入</VButton>
        <VButton size="sm" :loading="isVerifyingAllLinks" @click="handleVerifyAllLinks">
          <template #icon>
            <RiPulseLine />
          </template>
          检测全部
        </VButton>
      </VSpace>

      <div class=":uno: flex items-center gap-2">
        <FilterDropdown v-model="selectedStatusFilter" label="状态" :items="statusFilterOptions" />

        <button
          v-tooltip="'刷新'"
          type="button"
          class=":uno: group cursor-pointer rounded p-1 hover:bg-gray-200"
          @click="refetch()"
        >
          <IconRefreshLine
            :class="{ ':uno: animate-spin text-gray-900': isFetching }"
            class=":uno: h-4 w-4 text-gray-600 group-hover:text-gray-900"
          />
        </button>
      </div>
    </div>

    <VLoading v-if="isLoading" />

    <div class=":uno: space-y-4" v-else-if="filteredGroups.length">
      <LinksCard
        v-for="groupWithLinks in filteredGroups"
        :group-with-links="groupWithLinks"
        :key="groupWithLinks.group?.metadata.name"
      >
      </LinksCard>
    </div>
    <div
      v-else
      class=":uno: border border-gray-200 rounded-lg border-dashed bg-white py-12 text-center text-sm text-gray-500"
    >
      暂无符合条件的链接
    </div>
  </div>

  <GroupCreationModal v-if="groupCreationModalVisible" @close="groupCreationModalVisible = false" />
  <GroupSortModal v-if="groupSortModalVisible" @close="groupSortModalVisible = false" />
  <LinkImportModal v-if="linkImportModalVisible" @close="linkImportModalVisible = false" />
</template>
