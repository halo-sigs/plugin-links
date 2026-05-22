<script lang="ts" setup>
import { linksConsoleApiClient } from "@/api";
import type { LinkFormState } from "@/types";
import { Toast, VLoading } from "@halo-dev/components";
import { nextTick, onMounted, ref, shallowRef, toRaw } from "vue";
import MdiRss from "~icons/mdi/rss";
import MdiWebRefresh from "~icons/mdi/web-refresh";

const props = defineProps<{
  name?: string;
  formState?: LinkFormState;
}>();

const emit = defineEmits<{
  (event: "submit", data: LinkFormState): void;
}>();

type LinkFormData = LinkFormState & {
  rss: {
    enabled: boolean;
    feedUrl: string;
  };
};

const data = ref<LinkFormData>({
  url: "",
  displayName: "",
  rss: {
    enabled: false,
    feedUrl: "",
  },
});

onMounted(() => {
  if (props.formState) {
    const formState = toRaw(props.formState);
    data.value = {
      ...formState,
      rss: {
        enabled: formState.rss?.enabled ?? false,
        feedUrl: formState.rss?.feedUrl || "",
      },
    };
  }
});

const isFetchingLinkDetail = shallowRef(false);
const isDiscoveringFeed = shallowRef(false);

const handleGetLinkDetail = async () => {
  if (isFetchingLinkDetail.value) {
    return;
  }
  if (!data.value.url) {
    return;
  }
  isFetchingLinkDetail.value = true;
  try {
    const { data: linkDetail } = await linksConsoleApiClient.link.getLinkDetail({
      url: data.value.url,
    });

    data.value.displayName = linkDetail.title || "";
    data.value.logo = linkDetail.icon;
    data.value.description = linkDetail.description;

    Toast.info("获取链接详情成功");
  } finally {
    isFetchingLinkDetail.value = false;
  }
};

const handleDiscoverFeed = async () => {
  if (isDiscoveringFeed.value) {
    return;
  }
  if (!data.value.url) {
    return;
  }
  isDiscoveringFeed.value = true;
  try {
    const { data: result } = await linksConsoleApiClient.feed.discoverLinkFeed({
      url: data.value.url,
    });
    if (result.feedUrl) {
      data.value.rss = {
        enabled: true,
        feedUrl: result.feedUrl,
      };
      Toast.success("发现订阅地址");
      return;
    }
    Toast.info("未发现订阅地址");
  } catch {
    Toast.error("发现订阅地址失败");
  } finally {
    isDiscoveringFeed.value = false;
  }
};

const annotationsForm = ref();

async function onSubmit(formData: LinkFormState) {
  annotationsForm.value?.handleSubmit();
  await nextTick();

  const { customAnnotations, annotations, customFormInvalid, specFormInvalid } = annotationsForm.value || {};
  if (customFormInvalid || specFormInvalid) {
    return;
  }

  emit("submit", {
    ...formData,
    rss: {
      enabled: data.value.rss.enabled,
      feedUrl: data.value.rss.feedUrl,
    },
    annotations: {
      ...annotations,
      ...customAnnotations,
    },
  });
}
</script>
<template>
  <FormKit id="link-form" name="link-form" type="form" :config="{ validationVisibility: 'submit' }" @submit="onSubmit">
    <div class=":uno: md:grid md:grid-cols-4 md:gap-6">
      <div class=":uno: md:col-span-1">
        <div class=":uno: sticky top-0">
          <span class=":uno: text-base text-gray-900 font-medium"> 常规 </span>
        </div>
      </div>
      <div class=":uno: mt-5 md:col-span-3 md:mt-0 space-y-4">
        <FormKit type="url" name="url" v-model="data.url" validation="required" label="网站地址">
          <template #suffix>
            <button
              type="button"
              aria-label="获取网站信息"
              v-tooltip="{
                content: '获取网站信息',
              }"
              class=":uno: group h-full flex cursor-pointer items-center border-0 bg-transparent px-3 transition-all"
              @click="handleGetLinkDetail"
            >
              <VLoading v-if="isFetchingLinkDetail" class=":uno: size-4 text-gray-500 group-hover:text-gray-700" />
              <MdiWebRefresh v-else class=":uno: size-4 text-gray-500 group-hover:text-gray-700" />
            </button>
          </template>
        </FormKit>
        <FormKit
          type="text"
          name="displayName"
          v-model="data.displayName"
          validation="required"
          label="网站名称"
        ></FormKit>
        <FormKit type="attachment" name="logo" v-model="data.logo" label="Logo"></FormKit>
        <FormKit type="textarea" name="description" v-model="data.description" label="描述"></FormKit>
      </div>
    </div>

    <div class=":uno: py-5">
      <div class=":uno: border-t border-gray-200"></div>
    </div>

    <div class=":uno: md:grid md:grid-cols-4 md:gap-6">
      <div class=":uno: md:col-span-1">
        <div class=":uno: sticky top-0">
          <span class=":uno: text-base text-gray-900 font-medium"> RSS 订阅 </span>
        </div>
      </div>
      <div class=":uno: mt-5 md:col-span-3 md:mt-0 space-y-4">
        <FormKit type="checkbox" name="rssEnabled" v-model="data.rss.enabled" label="启用 RSS 订阅"></FormKit>
        <FormKit type="url" name="rssFeedUrl" v-model="data.rss.feedUrl" label="订阅地址">
          <template #suffix>
            <button
              type="button"
              aria-label="发现订阅地址"
              v-tooltip="{
                content: '发现订阅地址',
              }"
              class=":uno: group h-full flex cursor-pointer items-center border-0 bg-transparent px-3 transition-all"
              @click="handleDiscoverFeed"
            >
              <VLoading v-if="isDiscoveringFeed" class=":uno: size-4 text-gray-500 group-hover:text-gray-700" />
              <MdiRss v-else class=":uno: size-4 text-gray-500 group-hover:text-gray-700" />
            </button>
          </template>
        </FormKit>
      </div>
    </div>
  </FormKit>

  <div class=":uno: py-5">
    <div class=":uno: border-t border-gray-200"></div>
  </div>

  <div class=":uno: md:grid md:grid-cols-4 md:gap-6">
    <div class=":uno: md:col-span-1">
      <div class=":uno: sticky top-0">
        <span class=":uno: text-base text-gray-900 font-medium"> 元数据 </span>
      </div>
    </div>
    <div class=":uno: mt-5 md:col-span-3 md:mt-0 space-y-4">
      <AnnotationsForm
        :key="name"
        ref="annotationsForm"
        :value="formState?.annotations || {}"
        kind="Link"
        group="core.halo.run"
      />
    </div>
  </div>
</template>
