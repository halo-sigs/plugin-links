<script lang="ts" setup>
import { linksConsoleApiClient } from "@/api";
import type { LinkFormState } from "@/types";
import { Toast, VButton, VLoading } from "@halo-dev/components";
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
    feedUrls: string[];
  };
};

const data = ref<LinkFormData>({
  url: "",
  displayName: "",
  rss: {
    enabled: false,
    feedUrls: [],
  },
});
const rssFeedUrlsText = shallowRef("");

onMounted(() => {
  if (props.formState) {
    const formState = toRaw(props.formState);
    const feedUrls = [...(formState.rss?.feedUrls || [])];
    data.value = {
      ...formState,
      rss: {
        enabled: formState.rss?.enabled ?? false,
        feedUrls,
      },
    };
    rssFeedUrlsText.value = feedUrlsToText(feedUrls);
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
    const discoveredFeedUrls = normalizeFeedUrls(result.feedUrls || []);
    if (discoveredFeedUrls.length) {
      data.value.rss.enabled = true;
      const feedUrls = mergeFeedUrls(parseFeedUrlsText(rssFeedUrlsText.value), discoveredFeedUrls);
      data.value.rss.feedUrls = feedUrls;
      rssFeedUrlsText.value = feedUrlsToText(feedUrls);
      Toast.success(`发现 ${discoveredFeedUrls.length} 个订阅地址`);
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

function normalizeFeedUrls(feedUrls: string[]) {
  return [...new Set(feedUrls.map((feedUrl) => feedUrl.trim()).filter(Boolean))];
}

function parseFeedUrlsText(value: string) {
  return normalizeFeedUrls(value.split(/\r?\n/));
}

function feedUrlsToText(feedUrls: string[]) {
  return normalizeFeedUrls(feedUrls).join("\n");
}

function mergeFeedUrls(currentFeedUrls: string[], discoveredFeedUrls: string[]) {
  return normalizeFeedUrls([...currentFeedUrls, ...discoveredFeedUrls]);
}

async function onSubmit() {
  annotationsForm.value?.handleSubmit();
  await nextTick();

  const { customAnnotations, annotations, customFormInvalid, specFormInvalid } = annotationsForm.value || {};
  if (customFormInvalid || specFormInvalid) {
    return;
  }
  const feedUrls = parseFeedUrlsText(rssFeedUrlsText.value);
  data.value.rss.feedUrls = feedUrls;
  rssFeedUrlsText.value = feedUrlsToText(feedUrls);
  if (data.value.rss.enabled && !feedUrls.length) {
    Toast.error("请至少填写一个订阅地址");
    return;
  }

  emit("submit", {
    url: data.value.url,
    displayName: data.value.displayName,
    logo: data.value.logo,
    description: data.value.description,
    rss: {
      enabled: data.value.rss.enabled,
      feedUrls,
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
    <div>
      <div class=":uno: md:grid md:grid-cols-4 md:gap-6">
        <div class=":uno: md:col-span-1">
          <div class=":uno: sticky top-0">
            <span class=":uno: text-base text-gray-900 font-medium"> 常规 </span>
          </div>
        </div>
        <div class=":uno: mt-5 md:col-span-3 md:mt-0 divide-y divide-gray-100">
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
        <div class=":uno: mt-5 md:col-span-3 md:mt-0 divide-y divide-gray-100">
          <FormKit type="checkbox" name="rssEnabled" v-model="data.rss.enabled" label="启用 RSS 订阅"></FormKit>
          <FormKit
            v-if="data.rss.enabled"
            type="code"
            name="rssFeedUrlsText"
            v-model="rssFeedUrlsText"
            label="订阅地址"
            help="每行一个 RSS 或 Atom 地址"
            language="yaml"
            height="160px"
            placeholder="https://example.com/rss.xml&#10;https://example.com/atom.xml"
            :classes="{ inner: '!max-w-none' }"
          ></FormKit>
          <div class=":uno: pt-4">
            <VButton size="sm" @click="handleDiscoverFeed" :loading="isDiscoveringFeed">
              <template #icon>
                <MdiRss class=":uno: size-4 text-gray-500" />
              </template>
              发现订阅地址
            </VButton>
          </div>
        </div>
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
    <div class=":uno: mt-5 md:col-span-3 md:mt-0 divide-y divide-gray-100">
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
