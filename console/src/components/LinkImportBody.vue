<script lang="ts" setup>
import type { LinkGroup } from "@/api/generated";
import { useLinkGroupFetch } from "@/composables/use-group-fetch";
import { type LinkImportSubmitData, useLinkImport } from "@/composables/use-link-import";
import { Toast, VButton, VSpace } from "@halo-dev/components";
import { computed, watch } from "vue";
import LinkImportPreviewTable from "./LinkImportPreviewTable.vue";

const props = defineProps<{
  group?: LinkGroup;
  isImporting?: boolean;
}>();

const emit = defineEmits<{
  (event: "submit", data: LinkImportSubmitData): void;
}>();

const { data: groups } = useLinkGroupFetch();
const {
  step,
  rawText,
  selectedGroupName,
  enableScraping,
  parsedItems,
  isParsing,
  previewSummary,
  importableCount,
  parseInput,
  backToPaste,
  getImportableItems,
} = useLinkImport({
  initialGroupName: props.group?.metadata.name,
});

watch(
  () => props.group?.metadata.name,
  (groupName) => {
    selectedGroupName.value = groupName || "";
  },
  { immediate: true },
);

const groupOptions = computed(() => [
  { value: "", label: "未分组" },
  ...(groups.value || []).map((group) => ({
    value: group.metadata.name,
    label: group.spec?.displayName || group.metadata.name,
  })),
]);

async function handleParse() {
  const parsed = await parseInput();
  if (!parsed) {
    Toast.warning("请输入链接列表");
  }
}

function handleImport() {
  const itemsToImport = getImportableItems();
  if (itemsToImport.length === 0) {
    Toast.warning("没有可导入的链接");
    return;
  }
  emit("submit", { items: itemsToImport, groupName: selectedGroupName.value });
}
</script>

<template>
  <div class=":uno: h-full flex flex-col">
    <div class=":uno: flex-1 overflow-y-auto">
      <div v-show="step === 'paste'" class=":uno: space-y-4">
        <div v-if="!group">
          <FormKit v-model="selectedGroupName" type="select" label="导入到分组" :options="groupOptions" />
        </div>

        <FormKit
          v-model="rawText"
          type="code"
          label="链接列表"
          help="每行一个，格式：URL|名称|描述|图标"
          language="yaml"
          height="260px"
          placeholder="https://example.com|示例网站|这是一个示例网站|https://example.com/favicon.ico"
          :classes="{ inner: '!max-w-none' }"
        />

        <FormKit
          v-model="enableScraping"
          type="checkbox"
          label="在线解析（自动抓取标题、描述、图标）"
          help="解析时间较长，请耐心等待"
        />
      </div>

      <div v-show="step === 'preview'" class=":uno: space-y-4">
        <div class=":uno: rounded-md bg-gray-50 px-3 py-2 text-sm text-gray-600">
          共解析 {{ previewSummary.total }} 条，在线解析 {{ previewSummary.scraped }} 条，手动
          {{ previewSummary.manual }} 条，失败 {{ previewSummary.failed }} 条，已选
          {{ previewSummary.selected }} 条，可导入 {{ importableCount }} 条
        </div>

        <LinkImportPreviewTable v-model:items="parsedItems" />
      </div>
    </div>

    <div class=":uno: flex justify-start border-t border-gray-100 pt-4">
      <template v-if="step === 'paste'">
        <VButton type="secondary" :loading="isParsing" @click="handleParse">解析</VButton>
      </template>
      <template v-else>
        <VSpace>
          <VButton type="secondary" :loading="props.isImporting" @click="handleImport">
            确认导入（{{ importableCount }}）
          </VButton>
          <VButton :disabled="props.isImporting" @click="backToPaste">返回</VButton>
        </VSpace>
      </template>
    </div>
  </div>
</template>
