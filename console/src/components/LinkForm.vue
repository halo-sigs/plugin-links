<script lang="ts" setup>
import { linksConsoleApiClient, linkAiApiClient } from "@/api";
import type { LinkCommentAnalysisResult, LinkCommentDTO } from "@/api";
import type { LinkFormState } from "@/types";
import { Toast, VButton, VLoading } from "@halo-dev/components";
import { nextTick, onMounted, ref, shallowRef, toRaw } from "vue";
import MdiWebRefresh from "~icons/mdi/web-refresh";
import Rss2FillIcon from "~icons/mingcute/rss-2-fill";
import MdiCommentTextOutline from "~icons/mdi/comment-text-outline";
import MdiRobot from "~icons/mdi/robot";

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
  verification: {
    backlinkScanUrl: string;
  };
};

const data = ref<LinkFormData>({
  url: "",
  displayName: "",
  rss: {
    enabled: false,
    feedUrls: [],
  },
  verification: {
    backlinkScanUrl: "",
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
      verification: {
        backlinkScanUrl: formState.verification?.backlinkScanUrl || "",
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
    // Halo's API interceptor shows request failure toasts.
  } finally {
    isDiscoveringFeed.value = false;
  }
};

const annotationsForm = ref();

// AI comment extraction
const showAiExtract = shallowRef(false);
const isLoadingComments = shallowRef(false);
const isExtracting = shallowRef(false);
const recentComments = ref<LinkCommentDTO[]>([]);
const selectedCommentName = shallowRef("");
const manualCommentText = shallowRef("");

async function handleFetchComments() {
  if (isLoadingComments.value) return;
  isLoadingComments.value = true;
  try {
    const { data } = await linkAiApiClient.listRecentComments();
    recentComments.value = data;
    if (!data.length) {
      Toast.info("暂无已审核的评论");
    }
  } catch {
    // Halo's API interceptor shows request failure toasts.
  } finally {
    isLoadingComments.value = false;
  }
}

function selectComment(comment: LinkCommentDTO) {
  selectedCommentName.value = comment.name;
  manualCommentText.value = comment.raw || comment.content || "";
}

async function handleAiExtract() {
  const content = manualCommentText.value.trim();
  if (!content) {
    Toast.error("请选择一条评论或输入评论内容");
    return;
  }
  if (isExtracting.value) return;
  isExtracting.value = true;
  try {
    const { data: result } = await linkAiApiClient.extractFromComment(content);
    applyExtractedResult(result);
    Toast.success("AI 识别成功");
    showAiExtract.value = false;
  } catch (e: any) {
    const msg = e?.response?.data?.error || "AI 识别失败，请检查后重试或手动填写";
    Toast.error(msg);
  } finally {
    isExtracting.value = false;
  }
}

function applyExtractedResult(result: LinkCommentAnalysisResult) {
  if (result.url) data.value.url = result.url;
  if (result.displayName) data.value.displayName = result.displayName;
  if (result.logo) data.value.logo = result.logo;
  if (result.description) data.value.description = result.description;
}

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

function verificationSpec(backlinkScanUrl: string) {
  const normalizedBacklinkScanUrl = backlinkScanUrl.trim();
  if (!normalizedBacklinkScanUrl) {
    return undefined;
  }
  return {
    backlinkScanUrl: normalizedBacklinkScanUrl,
  };
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
    verification: verificationSpec(data.value.verification.backlinkScanUrl),
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
            <span class=":uno: text-base text-gray-900 font-medium"> AI 识别 </span>
          </div>
        </div>
        <div class=":uno: mt-5 md:col-span-3 md:mt-0">
          <div v-if="!showAiExtract" class=":uno: flex items-center gap-2">
            <VButton size="sm" type="secondary" @click="showAiExtract = true">
              <template #icon>
                <MdiRobot class=":uno: size-4" />
              </template>
              从评论识别
            </VButton>
            <span class=":uno: text-xs text-gray-500">通过 AI 从访客评论中提取友链信息</span>
          </div>
          <div v-else class=":uno: space-y-3 rounded-lg bg-gray-50 p-4">
            <div class=":uno: flex items-center justify-between">
              <span class=":uno: text-sm font-medium text-gray-900">从评论中识别友链</span>
              <button
                type="button"
                class=":uno: text-xs text-gray-500 hover:text-gray-700"
                @click="showAiExtract = false"
              >
                收起
              </button>
            </div>

            <div class=":uno: flex items-center gap-2">
              <VButton size="sm" :loading="isLoadingComments" @click="handleFetchComments">
                <template #icon>
                  <MdiCommentTextOutline class=":uno: size-4" />
                </template>
                获取最新评论
              </VButton>
            </div>

            <div
              v-if="recentComments.length"
              class=":uno: max-h-48 overflow-y-auto space-y-2 rounded border border-gray-200 bg-white p-2"
            >
              <div
                v-for="comment in recentComments"
                :key="comment.name"
                class=":uno: cursor-pointer rounded px-2 py-1.5 text-sm transition-colors"
                :class="selectedCommentName === comment.name ? 'bg-blue-50 text-blue-700' : 'hover:bg-gray-50 text-gray-700'"
                @click="selectComment(comment)"
              >
                <div class=":uno: flex items-center gap-2">
                  <input
                    type="radio"
                    :checked="selectedCommentName === comment.name"
                    class=":uno: cursor-pointer"
                  />
                  <span class=":uno: font-medium">{{ comment.ownerName || '匿名' }}</span>
                  <span class=":uno: text-xs text-gray-400">{{ comment.creationTime ? new Date(comment.creationTime).toLocaleString() : '' }}</span>
                </div>
                <div class=":uno: mt-0.5 truncate text-xs text-gray-500">
                  {{ comment.raw || comment.content }}
                </div>
              </div>
            </div>

            <div>
              <label class=":uno: block text-xs font-medium text-gray-700 mb-1">评论内容（可手动粘贴或编辑）</label>
              <textarea
                v-model="manualCommentText"
                rows="4"
                class=":uno: w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
                placeholder="粘贴或编辑评论内容，AI 将从中提取友链信息..."
              ></textarea>
            </div>

            <div class=":uno: flex justify-end">
              <VButton size="sm" type="secondary" :loading="isExtracting" @click="handleAiExtract">
                <template #icon>
                  <MdiRobot class=":uno: size-4" />
                </template>
                AI 识别
              </VButton>
            </div>
          </div>
        </div>
      </div>

      <div class=":uno: py-5">
        <div class=":uno: border-t border-gray-200"></div>
      </div>

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
          <FormKit type="textarea" name="description" v-model="data.description" label="描述" auto-height></FormKit>
        </div>
      </div>

      <div class=":uno: py-5">
        <div class=":uno: border-t border-gray-200"></div>
      </div>

      <div class=":uno: md:grid md:grid-cols-4 md:gap-6">
        <div class=":uno: md:col-span-1">
          <div class=":uno: sticky top-0">
            <span class=":uno: text-base text-gray-900 font-medium"> 链接检测 </span>
          </div>
        </div>
        <div class=":uno: mt-5 md:col-span-3 md:mt-0 divide-y divide-gray-100">
          <FormKit
            type="url"
            name="backlinkScanUrl"
            v-model="data.verification.backlinkScanUrl"
            label="反链检测页面"
            help="填写对方固定放置本站链接的页面，留空则不检测反链"
            placeholder="https://example.com/links"
          ></FormKit>
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
            height="70px"
            placeholder="https://example.com/rss.xml&#10;https://example.com/atom.xml"
            :classes="{ inner: '!max-w-none' }"
          ></FormKit>
          <div v-if="data.rss.enabled" class=":uno: pt-4">
            <VButton size="sm" @click="handleDiscoverFeed" :loading="isDiscoveringFeed">
              <template #icon>
                <Rss2FillIcon class=":uno: size-4 text-gray-500" />
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
