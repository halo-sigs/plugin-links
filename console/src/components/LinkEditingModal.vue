<script lang="ts" setup>
import type { Link, LinkDetail } from "@/types";
import { axiosInstance } from "@halo-dev/api-client";
import { Toast, VButton, VLoading, VModal, VSpace } from "@halo-dev/components";
import cloneDeep from "lodash.clonedeep";
import { computed, inject, nextTick, ref, watch, type Ref } from "vue";
import MdiWebRefresh from "~icons/mdi/web-refresh";

const props = withDefaults(
  defineProps<{
    visible: boolean;
    link?: Link;
  }>(),
  {
    visible: false,
    link: undefined,
  }
);

const emit = defineEmits<{
  (event: "update:visible", value: boolean): void;
  (event: "close"): void;
}>();

const initialFormState: Link = {
  metadata: {
    name: "",
    generateName: "link-",
  },
  spec: {
    displayName: "",
    url: "",
    logo: "",
    groupName: "",
  },
  kind: "Link",
  apiVersion: "core.halo.run/v1alpha1",
};

const formState = ref<Link>(cloneDeep(initialFormState));
const saving = ref<boolean>(false);
const formVisible = ref(false);

const groupQuery = inject<Ref<string>>("groupQuery", ref(""));

const isUpdateMode = computed(() => {
  return !!formState.value.metadata.creationTimestamp;
});

const modalTitle = computed(() => {
  return isUpdateMode.value ? "编辑链接" : "新建链接";
});

const onVisibleChange = (visible: boolean) => {
  emit("update:visible", visible);
  if (!visible) {
    emit("close");
  }
};

const handleResetForm = () => {
  formState.value = cloneDeep(initialFormState);
};

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      formVisible.value = true;
    } else {
      setTimeout(() => {
        formVisible.value = false;
        handleResetForm();
      }, 200);
    }
  }
);

watch(
  () => props.link,
  (link) => {
    if (link) {
      formState.value = cloneDeep(link);
    }
  }
);

const annotationsFormRef = ref();

const handleSaveLink = async () => {
  annotationsFormRef.value?.handleSubmit();
  await nextTick();

  const { customAnnotations, annotations, customFormInvalid, specFormInvalid } =
    annotationsFormRef.value || {};
  if (customFormInvalid || specFormInvalid) {
    return;
  }

  formState.value.metadata.annotations = {
    ...annotations,
    ...customAnnotations,
  };

  try {
    saving.value = true;
    if (isUpdateMode.value) {
      await axiosInstance.put<Link>(
        `/apis/core.halo.run/v1alpha1/links/${formState.value.metadata.name}`,
        formState.value
      );
    } else {
      formState.value.spec.groupName = groupQuery.value;
      await axiosInstance.post<Link>(
        `/apis/core.halo.run/v1alpha1/links`,
        formState.value
      );
    }

    Toast.success("保存成功");

    onVisibleChange(false);
  } catch (e) {
    console.error(e);
  } finally {
    saving.value = false;
  }
};

const loading = ref(false);

const handleGetLinkDetail = async () => {
  if (loading.value) {
    return;
  }
  const url = formState.value.spec.url;
  if (!url) {
    return;
  }
  loading.value = true;
  try {
    const { data } = await axiosInstance.get<LinkDetail>(
      `/apis/api.plugin.halo.run/v1alpha1/plugins/PluginLinks/link-detail?url=${url}`
    );

    formState.value.spec.displayName = data.title || "";
    formState.value.spec.logo = data.icon;
    formState.value.spec.description = data.description;

    Toast.info("获取链接详情成功");
  } catch (e) {
    Toast.error("获取链接详情失败");
  } finally {
    loading.value = false;
  }
};
</script>
<template>
  <VModal
    :title="modalTitle"
    :visible="visible"
    :width="650"
    @update:visible="onVisibleChange"
  >
    <template #actions>
      <slot name="append-actions" />
    </template>

    <FormKit
      v-if="formVisible"
      id="link-form"
      v-model="formState.spec"
      name="link-form"
      type="form"
      :config="{ validationVisibility: 'submit' }"
      :disabled="loading"
      @submit="handleSaveLink"
    >
      <div class="md:grid md:grid-cols-4 md:gap-6">
        <div class="md:col-span-1">
          <div class="sticky top-0">
            <span class="text-base font-medium text-gray-900"> 常规 </span>
          </div>
        </div>
        <div class="mt-5 divide-y divide-gray-100 md:col-span-3 md:mt-0">
          <FormKit type="url" name="url" validation="required" label="网站地址">
            <template #suffix>
              <div
                v-tooltip="{
                  content: '获取网站信息',
                }"
                class="group flex h-full cursor-pointer items-center px-3 transition-all"
                @click="handleGetLinkDetail"
              >
                <VLoading
                  v-if="loading"
                  class="h-4 w-4 text-gray-500 group-hover:text-gray-700"
                />
                <MdiWebRefresh
                  v-else
                  class="h-4 w-4 text-gray-500 group-hover:text-gray-700"
                />
              </div>
            </template>
          </FormKit>
          <FormKit
            type="text"
            name="displayName"
            validation="required"
            label="网站名称"
          ></FormKit>
          <FormKit type="attachment" name="logo" label="Logo"></FormKit>
          <FormKit type="textarea" name="description" label="描述"></FormKit>
        </div>
      </div>
    </FormKit>

    <div class="py-5">
      <div class="border-t border-gray-200"></div>
    </div>

    <div class="md:grid md:grid-cols-4 md:gap-6">
      <div class="md:col-span-1">
        <div class="sticky top-0">
          <span class="text-base font-medium text-gray-900"> 元数据 </span>
        </div>
      </div>
      <div class="mt-5 divide-y divide-gray-100 md:col-span-3 md:mt-0">
        <AnnotationsForm
          v-if="visible"
          :key="formState.metadata.name"
          ref="annotationsFormRef"
          :value="formState.metadata.annotations"
          kind="Link"
          group="core.halo.run"
        />
      </div>
    </div>

    <template #footer>
      <VSpace>
        <VButton
          :loading="saving"
          type="secondary"
          @click="$formkit.submit('link-form')"
        >
          提交
        </VButton>
        <VButton @click="onVisibleChange(false)">取消</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
