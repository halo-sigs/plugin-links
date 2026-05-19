<script lang="ts" setup>
import { linksConsoleApiClient } from "@/api";
import { LinkGroup } from "@/api/generated";
import { useLinkGroupFetch } from "@/composables/use-group-fetch";
import { Toast } from "@halo-dev/components";
import { computed, ref, watch } from "vue";

const props = defineProps<{
  group?: LinkGroup;
  isImporting?: boolean;
}>();

const emit = defineEmits<{
  (event: "submit", data: { items: ParsedItem[]; groupName: string }): void;
}>();

const { data: groups } = useLinkGroupFetch();

export interface ParsedItem {
  id: number;
  url: string;
  displayName: string;
  description: string;
  logo: string;
  status: "pending" | "scraping" | "success" | "error";
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

const importableCount = computed(() => parsedItems.value.filter((i) => i.checked && i.status !== "error").length);

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
      status: "pending",
      errorMessage: "",
      checked: true,
    };
  });
}

function validateItems() {
  for (const item of parsedItems.value) {
    if (!item.url) {
      item.status = "error";
      item.errorMessage = "URL 不能为空";
      continue;
    }
    if (!/^https?:\/\//i.test(item.url)) {
      item.status = "error";
      item.errorMessage = "URL 格式不正确";
      continue;
    }
    if (!item.displayName && !enableScraping.value) {
      item.status = "error";
      item.errorMessage = "名称为空且未启用在线解析";
      continue;
    }
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
        item.errorMessage = "获取链接详情失败";
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
  validateItems();

  if (enableScraping.value) {
    isParsing.value = true;
    await scrapeItems();
    isParsing.value = false;
  }

  step.value = "preview";
}

function handleBack() {
  step.value = "paste";
}

function handleToggleAll(e: Event) {
  const checked = (e.target as HTMLInputElement).checked;
  parsedItems.value.forEach((item) => (item.checked = checked));
}

function revalidateItem(item: ParsedItem) {
  if (item.status !== "error") return;
  if (item.url && /^https?:\/\//i.test(item.url) && item.displayName) {
    item.status = "pending";
    item.errorMessage = "";
  }
}

watch(
  parsedItems,
  (items) => {
    for (const item of items) {
      revalidateItem(item);
    }
  },
  { deep: true }
);

function handleImport() {
  for (const item of parsedItems.value) {
    revalidateItem(item);
  }

  const itemsToImport = parsedItems.value.filter((item) => item.checked && item.status !== "error");
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
          <FormKit
            v-model="selectedGroupName"
            type="select"
            label="导入到分组"
            :options="groupOptions"
          />
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
        <div class=":uno: text-sm text-gray-500">
          共解析 {{ parsedItems.length }} 条，成功 {{ parsedItems.filter((i) => i.status === "success").length }} 条，失败
          {{ parsedItems.filter((i) => i.status === "error").length }} 条，选中
          {{ parsedItems.filter((i) => i.checked).length }}
          条
        </div>

        <div class=":uno: border rounded-md">
          <table class=":uno: min-w-full divide-y divide-gray-200">
            <thead class=":uno: bg-gray-50">
              <tr>
                <th
                  class=":uno: px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider w-10"
                >
                  <input
                    type="checkbox"
                    :checked="parsedItems.every((i) => i.checked)"
                    @change="handleToggleAll"
                    class=":uno: h-4 w-4 rounded border-gray-300 text-indigo-600"
                  />
                </th>
                <th class=":uno: px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">名称</th>
                <th class=":uno: px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">URL</th>
                <th class=":uno: px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">状态</th>
              </tr>
            </thead>
            <tbody class=":uno: bg-white divide-y divide-gray-200">
              <tr v-for="item in parsedItems" :key="item.id" :class="item.checked ? '' : 'opacity-50'">
                <td class=":uno: px-3 py-2">
                  <input
                    type="checkbox"
                    v-model="item.checked"
                    class=":uno: h-4 w-4 rounded border-gray-300 text-indigo-600"
                  />
                </td>
                <td class=":uno: px-3 py-2">
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
                <td class=":uno: px-3 py-2">
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
                <td class=":uno: px-3 py-2 whitespace-nowrap">
                  <span
                    v-if="item.status === 'scraping'"
                    class=":uno: inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-blue-100 text-blue-800"
                  >
                    解析中...
                  </span>
                  <span
                    v-else-if="item.status === 'success'"
                    class=":uno: inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-green-100 text-green-800"
                  >
                    成功
                  </span>
                  <span
                    v-else-if="item.status === 'error'"
                    class=":uno: inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-red-100 text-red-800"
                    :title="item.errorMessage"
                  >
                    {{ item.errorMessage }}
                  </span>
                  <span
                    v-else
                    class=":uno: inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-800"
                  >
                    待处理
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Footer -->
    <div class=":uno: pt-4 flex gap-2 justify-end">
      <template v-if="step === 'paste'">
        <button
          :disabled="isParsing"
          class=":uno: px-4 py-2 bg-gray-900 text-white text-sm rounded-md hover:bg-gray-800 disabled:opacity-50"
          @click="handleParse"
        >
          {{ isParsing ? "解析中..." : "解析" }}
        </button>
      </template>
      <template v-else>
        <button
          :disabled="props.isImporting"
          class=":uno: px-4 py-2 bg-gray-900 text-white text-sm rounded-md hover:bg-gray-800 disabled:opacity-50"
          @click="handleImport"
        >
          {{ props.isImporting ? "导入中..." : `确认导入 (${importableCount})` }}
        </button>
        <button
          class=":uno: px-4 py-2 border border-gray-300 text-sm rounded-md hover:bg-gray-50"
          @click="handleBack"
        >
          返回
        </button>
      </template>
    </div>
  </div>
</template>
