<script lang="ts" setup>
import { linksConsoleApiClient } from "@/api";
import type { LinkGroup } from "@/api/generated";
import { useLinkGroupFetch } from "@/composables/use-group-fetch";
import { Toast, VButton, VSpace } from "@halo-dev/components";
import { computed, ref, watch } from "vue";

const props = defineProps<{
  group?: LinkGroup;
  isImporting?: boolean;
}>();

const emit = defineEmits<{
  (event: "submit", data: { items: ParsedItem[]; groupName: string }): void;
}>();

const { data: groups } = useLinkGroupFetch();

type ParsedItemStatus = "manual" | "scraping" | "success" | "error";

export interface ParsedItem {
  id: number;
  url: string;
  displayName: string;
  description: string;
  logo: string;
  status: ParsedItemStatus;
  errorMessage: string;
  checked: boolean;
}

const step = ref<"paste" | "preview">("paste");
const rawText = ref("");
const selectedGroupName = ref(props.group?.metadata.name || "");
const enableScraping = ref(false);
const parsedItems = ref<ParsedItem[]>([]);
const isParsing = ref(false);

const groupOptions = computed(() => [
  { value: "", label: "未分组" },
  ...(groups.value || []).map((g) => ({
    value: g.metadata.name,
    label: g.spec?.displayName || g.metadata.name,
  })),
]);

const previewSummary = computed(() => {
  const selected = parsedItems.value.filter((item) => item.checked).length;
  const scraped = parsedItems.value.filter((item) => item.status === "success").length;
  const manual = parsedItems.value.filter((item) => item.status === "manual").length;
  const failed = parsedItems.value.filter((item) => item.status === "error").length;

  return {
    total: parsedItems.value.length,
    selected,
    scraped,
    manual,
    failed,
  };
});

const importableCount = computed(() => {
  return parsedItems.value.filter((item) => item.checked && !getItemErrorMessage(item)).length;
});

const allItemsChecked = computed(() => parsedItems.value.length > 0 && parsedItems.value.every((item) => item.checked));
const isPartiallyChecked = computed(() => {
  const checkedCount = parsedItems.value.filter((item) => item.checked).length;
  return checkedCount > 0 && checkedCount < parsedItems.value.length;
});

function isHttpUrl(value: string) {
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}

function getItemErrorMessage(item: ParsedItem, { requireDisplayName = true } = {}) {
  if (!item.url.trim()) {
    return "URL 不能为空";
  }
  if (!isHttpUrl(item.url.trim())) {
    return "URL 格式不正确";
  }
  if (requireDisplayName && !item.displayName.trim()) {
    return "名称不能为空";
  }
  return "";
}

function applyItemValidation(item: ParsedItem, options?: { requireDisplayName?: boolean }) {
  if (item.status === "scraping") {
    return false;
  }

  const errorMessage = getItemErrorMessage(item, options);
  if (errorMessage) {
    item.status = "error";
    item.errorMessage = errorMessage;
    return false;
  }

  if (item.status === "error") {
    item.status = "manual";
  }
  item.errorMessage = "";
  return true;
}

function normalizeItem(item: ParsedItem): ParsedItem {
  return {
    ...item,
    url: item.url.trim(),
    displayName: item.displayName.trim(),
    description: item.description.trim(),
    logo: item.logo.trim(),
  };
}

function parseLines() {
  const lines = rawText.value
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0);

  parsedItems.value = lines.map((line, index) => {
    const segments = line.split("|").map((s) => s.trim());
    const url = segments[0] || "";

    return {
      id: index,
      url,
      displayName: segments[1] || "",
      description: segments[2] || "",
      logo: segments[3] || "",
      status: "manual",
      errorMessage: "",
      checked: true,
    };
  });
}

function validateItems(options?: { requireDisplayName?: boolean }) {
  for (const item of parsedItems.value) {
    applyItemValidation(item, options);
  }
}

async function scrapeItems() {
  const itemsToScrape = parsedItems.value.filter(
    (item) => item.checked && item.status !== "error" && (!item.displayName || !item.description || !item.logo),
  );

  if (itemsToScrape.length === 0) return;

  const concurrency = 3;
  const queue = [...itemsToScrape];

  async function worker() {
    while (queue.length > 0) {
      const item = queue.shift()!;
      item.status = "scraping";
      try {
        const { data: detail } = await linksConsoleApiClient.link.getLinkDetail({
          url: item.url,
        });
        item.displayName = item.displayName || detail.title || "";
        item.description = item.description || detail.description || "";
        item.logo = item.logo || detail.icon || "";
        item.status = "success";
      } catch {
        item.status = "error";
        item.errorMessage = "获取链接详情失败，请手动补充名称";
      }
    }
  }

  const workers = Array.from({ length: Math.min(concurrency, itemsToScrape.length) }, () => worker());
  await Promise.all(workers);
}

async function handleParse() {
  if (!rawText.value.trim()) {
    Toast.warning("请输入链接列表");
    return;
  }

  parseLines();
  validateItems({ requireDisplayName: !enableScraping.value });

  if (enableScraping.value) {
    isParsing.value = true;
    await scrapeItems();
    isParsing.value = false;
  }

  validateItems();
  step.value = "preview";
}

function handleBack() {
  step.value = "paste";
}

function handleToggleAll(e: Event) {
  const checked = (e.target as HTMLInputElement).checked;
  parsedItems.value.forEach((item) => (item.checked = checked));
}

watch(
  parsedItems,
  (items) => {
    if (step.value !== "preview") {
      return;
    }
    for (const item of items) {
      applyItemValidation(item);
    }
  },
  { deep: true },
);

function handleImport() {
  validateItems();

  const itemsToImport = parsedItems.value
    .filter((item) => item.checked && !getItemErrorMessage(item))
    .map((item) => normalizeItem(item));
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
      <!-- Paste Step -->
      <div v-show="step === 'paste'" class=":uno: space-y-4">
        <div v-if="!group">
          <FormKit v-model="selectedGroupName" type="select" label="导入到分组" :options="groupOptions" />
        </div>

        <FormKit
          v-model="rawText"
          type="textarea"
          label="链接列表"
          help="每行一个，格式：URL|名称|描述|图标"
          :rows="10"
          placeholder="https://example.com|示例网站|这是一个示例网站|https://example.com/favicon.ico"
        />

        <FormKit
          v-model="enableScraping"
          type="checkbox"
          label="在线解析（自动抓取标题、描述、图标）"
          help="解析时间较长，请耐心等待"
        />
      </div>

      <!-- Preview Step -->
      <div v-show="step === 'preview'" class=":uno: space-y-4">
        <div class=":uno: rounded-md bg-gray-50 px-3 py-2 text-sm text-gray-600">
          共解析 {{ previewSummary.total }} 条，在线解析 {{ previewSummary.scraped }} 条，手动
          {{ previewSummary.manual }} 条，失败 {{ previewSummary.failed }} 条，已选
          {{ previewSummary.selected }} 条，可导入 {{ importableCount }} 条
        </div>

        <div class=":uno: overflow-x-auto border border-gray-200 rounded-md">
          <table class=":uno: min-w-[720px] w-full divide-y divide-gray-200">
            <thead class=":uno: bg-gray-50">
              <tr>
                <th class=":uno: w-10 px-3 py-2 text-left text-xs text-gray-500 font-medium tracking-wider uppercase">
                  <input
                    type="checkbox"
                    :checked="allItemsChecked"
                    :indeterminate.prop="isPartiallyChecked"
                    @change="handleToggleAll"
                    class=":uno: h-4 w-4 border-gray-300 rounded text-indigo-600"
                  />
                </th>
                <th class=":uno: px-3 py-2 text-left text-xs text-gray-500 font-medium tracking-wider uppercase">
                  名称
                </th>
                <th class=":uno: px-3 py-2 text-left text-xs text-gray-500 font-medium tracking-wider uppercase">
                  URL
                </th>
                <th class=":uno: px-3 py-2 text-left text-xs text-gray-500 font-medium tracking-wider uppercase">
                  状态
                </th>
              </tr>
            </thead>
            <tbody class=":uno: bg-white divide-y divide-gray-200">
              <tr
                v-for="item in parsedItems"
                :key="item.id"
                :class="{
                  ':uno: opacity-50': !item.checked,
                  ':uno: bg-red-50/40': item.status === 'error',
                }"
              >
                <td class=":uno: px-3 py-3 align-top">
                  <input
                    type="checkbox"
                    v-model="item.checked"
                    class=":uno: h-4 w-4 border-gray-300 rounded text-indigo-600"
                  />
                </td>
                <td class=":uno: min-w-[220px] px-3 py-3 align-top">
                  <FormKit
                    :id="`displayName-${item.id}`"
                    :name="`displayName-${item.id}`"
                    type="text"
                    v-model="item.displayName"
                    :classes="{
                      outer: '!py-0',
                      wrapper: '!mb-0',
                      input: item.status === 'error' ? '!border-red-300' : '',
                    }"
                  />
                  <FormKit
                    :id="`description-${item.id}`"
                    :name="`description-${item.id}`"
                    type="text"
                    v-model="item.description"
                    placeholder="描述"
                    :classes="{
                      outer: '!py-0 !mt-1',
                      wrapper: '!mb-0',
                    }"
                  />
                </td>
                <td class=":uno: min-w-[280px] px-3 py-3 align-top">
                  <FormKit
                    :id="`url-${item.id}`"
                    :name="`url-${item.id}`"
                    type="text"
                    v-model="item.url"
                    :classes="{
                      outer: '!py-0',
                      wrapper: '!mb-0',
                      input: item.status === 'error' ? '!border-red-300' : '',
                    }"
                  />
                  <FormKit
                    :id="`logo-${item.id}`"
                    :name="`logo-${item.id}`"
                    type="text"
                    v-model="item.logo"
                    placeholder="图标 URL"
                    :classes="{
                      outer: '!py-0 !mt-1',
                      wrapper: '!mb-0',
                    }"
                  />
                </td>
                <td class=":uno: max-w-[160px] px-3 py-3 align-top">
                  <span
                    v-if="item.status === 'scraping'"
                    class=":uno: inline-flex items-center rounded bg-blue-100 px-2 py-0.5 text-xs text-blue-800 font-medium"
                  >
                    解析中...
                  </span>
                  <span
                    v-else-if="item.status === 'success'"
                    class=":uno: inline-flex items-center rounded bg-green-100 px-2 py-0.5 text-xs text-green-800 font-medium"
                  >
                    已解析
                  </span>
                  <span
                    v-else-if="item.status === 'error'"
                    class=":uno: max-w-full inline-flex items-center rounded bg-red-100 px-2 py-0.5 text-xs text-red-800 font-medium"
                    :title="item.errorMessage"
                  >
                    <span class=":uno: truncate">{{ item.errorMessage }}</span>
                  </span>
                  <span
                    v-else
                    class=":uno: inline-flex items-center rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-700 font-medium"
                  >
                    手动
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Footer -->
    <div class=":uno: flex justify-end border-t border-gray-100 pt-4">
      <template v-if="step === 'paste'">
        <VButton type="secondary" :loading="isParsing" @click="handleParse">解析</VButton>
      </template>
      <template v-else>
        <VSpace>
          <VButton type="secondary" :loading="props.isImporting" @click="handleImport">
            确认导入（{{ importableCount }}）
          </VButton>
          <VButton :disabled="props.isImporting" @click="handleBack">返回</VButton>
        </VSpace>
      </template>
    </div>
  </div>
</template>
