<script lang="ts" setup>
import { linksConsoleApiClient } from "@/api";
import { LinkFormState } from "@/types";
import { Toast, VLoading } from "@halo-dev/components";
import { nextTick, onMounted, ref, toRaw } from "vue";
import MdiWebRefresh from "~icons/mdi/web-refresh";

const props = defineProps<{
  name?: string;
  formState?: LinkFormState;
}>();

const emit = defineEmits<{
  (event: "submit", data: LinkFormState): void;
}>();

const data = ref<LinkFormState>({
  url: "",
  displayName: "",
});

onMounted(() => {
  if (props.formState) {
    data.value = toRaw(props.formState);
  }
});

const isFetchingLinkDetail = ref(false);

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

const annotationsForm = ref();

async function onSubmit(data: LinkFormState) {
  annotationsForm.value?.handleSubmit();
  await nextTick();

  const { customAnnotations, annotations, customFormInvalid, specFormInvalid } = annotationsForm.value || {};
  if (customFormInvalid || specFormInvalid) {
    return;
  }

  emit("submit", {
    ...data,
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
      <div class=":uno: mt-5 md:col-span-3 md:mt-0 divide-y divide-gray-100">
        <FormKit type="url" name="url" v-model="data.url" validation="required" label="网站地址">
          <template #suffix>
            <div
              v-tooltip="{
                content: '获取网站信息',
              }"
              class=":uno: group h-full flex cursor-pointer items-center px-3 transition-all"
              @click="handleGetLinkDetail"
            >
              <VLoading v-if="isFetchingLinkDetail" class=":uno: size-4 text-gray-500 group-hover:text-gray-700" />
              <MdiWebRefresh v-else class=":uno: size-4 text-gray-500 group-hover:text-gray-700" />
            </div>
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
