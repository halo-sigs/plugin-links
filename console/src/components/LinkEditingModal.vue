<script lang="ts" setup>
import { linksConsoleApiClient, linksCoreApiClient } from "@/api";
import { Link } from "@/api/generated";
import { Toast, VButton, VLoading, VModal, VSpace } from "@halo-dev/components";
import { cloneDeep } from "es-toolkit";
import { computed, inject, nextTick, onMounted, ref, useTemplateRef, watch, type Ref } from "vue";
import MdiWebRefresh from "~icons/mdi/web-refresh";

const props = withDefaults(
  defineProps<{
    link?: Link;
  }>(),
  {
    link: undefined,
  }
);

const emit = defineEmits<{
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
const modal = useTemplateRef<InstanceType<typeof VModal> | null>("modal");

const groupQuery = inject<Ref<string>>("groupQuery", ref(""));

const isUpdateMode = computed(() => {
  return !!formState.value.metadata.creationTimestamp;
});

const modalTitle = computed(() => {
  return isUpdateMode.value ? "编辑链接" : "新建链接";
});

onMounted(() => {
  if (props.link) {
    formState.value = cloneDeep(props.link);
  }
});

const annotationsFormRef = ref();

const handleSaveLink = async () => {
  annotationsFormRef.value?.handleSubmit();
  await nextTick();

  const { customAnnotations, annotations, customFormInvalid, specFormInvalid } = annotationsFormRef.value || {};
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
      await linksCoreApiClient.link.updateLink({
        name: formState.value.metadata.name,
        link: formState.value,
      });
    } else {
      formState.value.spec!.groupName = groupQuery.value;
      await linksCoreApiClient.link.createLink({
        link: formState.value,
      });
    }

    Toast.success("保存成功");

    modal.value?.close();
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
  const url = formState.value.spec?.url;
  if (!url) {
    return;
  }
  loading.value = true;
  try {
    const { data } = await linksConsoleApiClient.link.getLinkDetail({
      url: url,
    });

    formState.value.spec!.displayName = data.title || "";
    formState.value.spec!.logo = data.icon;
    formState.value.spec!.description = data.description;

    Toast.info("获取链接详情成功");
  } catch (e) {
    Toast.error("获取链接详情失败");
  } finally {
    loading.value = false;
  }
};
</script>
<template>
  <VModal :title="modalTitle" ref="modal" :width="650" @close="emit('close')">
    <template #actions>
      <slot name="append-actions" />
    </template>

    <FormKit
      id="link-form"
      v-model="formState.spec"
      name="link-form"
      type="form"
      :config="{ validationVisibility: 'submit' }"
      :disabled="loading"
      @submit="handleSaveLink"
    >
      <div class=":uno: md:grid md:grid-cols-4 md:gap-6">
        <div class=":uno: md:col-span-1">
          <div class=":uno: sticky top-0">
            <span class=":uno: text-base text-gray-900 font-medium"> 常规 </span>
          </div>
        </div>
        <div class=":uno: mt-5 md:col-span-3 md:mt-0 divide-y divide-gray-100">
          <FormKit type="url" name="url" validation="required" label="网站地址">
            <template #suffix>
              <div
                v-tooltip="{
                  content: '获取网站信息',
                }"
                class=":uno: group h-full flex cursor-pointer items-center px-3 transition-all"
                @click="handleGetLinkDetail"
              >
                <VLoading v-if="loading" class=":uno: size-4 text-gray-500 group-hover:text-gray-700" />
                <MdiWebRefresh v-else class=":uno: size-4 text-gray-500 group-hover:text-gray-700" />
              </div>
            </template>
          </FormKit>
          <FormKit type="text" name="displayName" validation="required" label="网站名称"></FormKit>
          <FormKit type="attachment" name="logo" label="Logo"></FormKit>
          <FormKit type="textarea" name="description" label="描述"></FormKit>
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
        <!-- @vue-ignore -->
        <VButton :loading="saving" type="secondary" @click="$formkit.submit('link-form')"> 提交 </VButton>
        <VButton @click="modal?.close()">取消</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
