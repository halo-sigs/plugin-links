<script lang="ts" setup>
import { getParsedItemStatusMeta, type ParsedItem, type ParsedItemStatusTone } from "@/composables/use-link-import";
import { computed } from "vue";

const items = defineModel<ParsedItem[]>("items", { required: true });

const allItemsChecked = computed(() => items.value.length > 0 && items.value.every((item) => item.checked));
const isPartiallyChecked = computed(() => {
  const checkedCount = items.value.filter((item) => item.checked).length;
  return checkedCount > 0 && checkedCount < items.value.length;
});
const statusMetaMap = computed(() => {
  return new Map(items.value.map((item) => [item.id, getParsedItemStatusMeta(item)]));
});

function getStatusMeta(item: ParsedItem) {
  return statusMetaMap.value.get(item.id) || getParsedItemStatusMeta(item);
}

function hasError(item: ParsedItem) {
  return getStatusMeta(item).tone === "error";
}

function handleToggleAll(e: Event) {
  const checked = (e.target as HTMLInputElement).checked;
  items.value.forEach((item) => (item.checked = checked));
}

function isStatusTone(item: ParsedItem, tone: ParsedItemStatusTone) {
  return getStatusMeta(item).tone === tone;
}
</script>

<template>
  <div class=":uno: md:hidden space-y-3">
    <label class=":uno: flex items-center gap-2 border border-gray-200 rounded-md bg-gray-50 px-3 py-2 text-sm">
      <input
        type="checkbox"
        :checked="allItemsChecked"
        :indeterminate.prop="isPartiallyChecked"
        class=":uno: h-4 w-4 border-gray-300 rounded text-indigo-600"
        @change="handleToggleAll"
      />
      <span>全选</span>
    </label>

    <div
      v-for="item in items"
      :key="item.id"
      class=":uno: border border-gray-200 rounded-md bg-white p-3 space-y-3"
      :class="{
        ':uno: opacity-50': !item.checked,
        ':uno: border-red-200 bg-red-50/30': hasError(item),
      }"
    >
      <div class=":uno: flex items-start justify-between gap-3">
        <input
          v-model="item.checked"
          type="checkbox"
          class=":uno: mt-1 h-4 w-4 border-gray-300 rounded text-indigo-600"
        />
        <span
          v-if="isStatusTone(item, 'scraping')"
          class=":uno: inline-flex items-center rounded bg-blue-100 px-2 py-0.5 text-xs text-blue-800 font-medium"
        >
          {{ getStatusMeta(item).label }}
        </span>
        <span
          v-else-if="isStatusTone(item, 'success')"
          class=":uno: inline-flex items-center rounded bg-green-100 px-2 py-0.5 text-xs text-green-800 font-medium"
        >
          {{ getStatusMeta(item).label }}
        </span>
        <span
          v-else-if="isStatusTone(item, 'error')"
          class=":uno: min-w-0 inline-flex items-center rounded bg-red-100 px-2 py-0.5 text-xs text-red-800 font-medium"
          :title="getStatusMeta(item).label"
        >
          <span class=":uno: truncate">{{ getStatusMeta(item).label }}</span>
        </span>
        <span
          v-else
          class=":uno: inline-flex items-center rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-700 font-medium"
        >
          {{ getStatusMeta(item).label }}
        </span>
      </div>

      <FormKit
        v-model="item.displayName"
        :id="`displayName-mobile-${item.id}`"
        :name="`displayName-mobile-${item.id}`"
        type="text"
        placeholder="名称"
        :classes="{
          outer: '!py-0',
          wrapper: '!mb-0',
          input: hasError(item) ? '!border-red-300' : '',
        }"
      />
      <FormKit
        v-model="item.url"
        :id="`url-mobile-${item.id}`"
        :name="`url-mobile-${item.id}`"
        type="text"
        placeholder="URL"
        :classes="{
          outer: '!py-0',
          wrapper: '!mb-0',
          input: hasError(item) ? '!border-red-300' : '',
        }"
      />
      <FormKit
        v-model="item.description"
        :id="`description-mobile-${item.id}`"
        :name="`description-mobile-${item.id}`"
        type="text"
        placeholder="描述"
        :classes="{
          outer: '!py-0',
          wrapper: '!mb-0',
        }"
      />
      <FormKit
        v-model="item.logo"
        :id="`logo-mobile-${item.id}`"
        :name="`logo-mobile-${item.id}`"
        type="text"
        placeholder="图标 URL"
        :classes="{
          outer: '!py-0',
          wrapper: '!mb-0',
        }"
      />
    </div>
  </div>

  <div class=":uno: hidden overflow-x-auto border border-gray-200 rounded-md md:block">
    <table class=":uno: min-w-[820px] w-full table-fixed divide-y divide-gray-200">
      <thead class=":uno: bg-gray-50">
        <tr>
          <th class=":uno: w-10 px-3 py-2 text-left text-xs text-gray-500 font-medium tracking-wider uppercase">
            <input
              type="checkbox"
              :checked="allItemsChecked"
              :indeterminate.prop="isPartiallyChecked"
              class=":uno: h-4 w-4 border-gray-300 rounded text-indigo-600"
              @change="handleToggleAll"
            />
          </th>
          <th class=":uno: w-[260px] px-3 py-2 text-left text-xs text-gray-500 font-medium tracking-wider uppercase">
            名称
          </th>
          <th class=":uno: w-[360px] px-3 py-2 text-left text-xs text-gray-500 font-medium tracking-wider uppercase">
            URL
          </th>
          <th class=":uno: w-[160px] px-3 py-2 text-left text-xs text-gray-500 font-medium tracking-wider uppercase">
            状态
          </th>
        </tr>
      </thead>
      <tbody class=":uno: bg-white divide-y divide-gray-200">
        <tr
          v-for="item in items"
          :key="item.id"
          :class="{
            ':uno: opacity-50': !item.checked,
            ':uno: bg-red-50/40': hasError(item),
          }"
        >
          <td class=":uno: px-3 py-3 align-top">
            <input
              v-model="item.checked"
              type="checkbox"
              class=":uno: h-4 w-4 border-gray-300 rounded text-indigo-600"
            />
          </td>
          <td class=":uno: px-3 py-3 align-top">
            <FormKit
              v-model="item.displayName"
              :id="`displayName-${item.id}`"
              :name="`displayName-${item.id}`"
              type="text"
              :classes="{
                outer: '!py-0',
                wrapper: '!mb-0',
                input: hasError(item) ? '!border-red-300' : '',
              }"
            />
            <FormKit
              v-model="item.description"
              :id="`description-${item.id}`"
              :name="`description-${item.id}`"
              type="text"
              placeholder="描述"
              :classes="{
                outer: '!py-0 !mt-1',
                wrapper: '!mb-0',
              }"
            />
          </td>
          <td class=":uno: px-3 py-3 align-top">
            <FormKit
              v-model="item.url"
              :id="`url-${item.id}`"
              :name="`url-${item.id}`"
              type="text"
              :classes="{
                outer: '!py-0',
                wrapper: '!mb-0',
                input: hasError(item) ? '!border-red-300' : '',
              }"
            />
            <FormKit
              v-model="item.logo"
              :id="`logo-${item.id}`"
              :name="`logo-${item.id}`"
              type="text"
              placeholder="图标 URL"
              :classes="{
                outer: '!py-0 !mt-1',
                wrapper: '!mb-0',
              }"
            />
          </td>
          <td class=":uno: px-3 py-3 align-top">
            <span
              v-if="isStatusTone(item, 'scraping')"
              class=":uno: inline-flex items-center rounded bg-blue-100 px-2 py-0.5 text-xs text-blue-800 font-medium"
            >
              {{ getStatusMeta(item).label }}
            </span>
            <span
              v-else-if="isStatusTone(item, 'success')"
              class=":uno: inline-flex items-center rounded bg-green-100 px-2 py-0.5 text-xs text-green-800 font-medium"
            >
              {{ getStatusMeta(item).label }}
            </span>
            <span
              v-else-if="isStatusTone(item, 'error')"
              class=":uno: max-w-full inline-flex items-center rounded bg-red-100 px-2 py-0.5 text-xs text-red-800 font-medium"
              :title="getStatusMeta(item).label"
            >
              <span class=":uno: truncate">{{ getStatusMeta(item).label }}</span>
            </span>
            <span
              v-else
              class=":uno: inline-flex items-center rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-700 font-medium"
            >
              {{ getStatusMeta(item).label }}
            </span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
