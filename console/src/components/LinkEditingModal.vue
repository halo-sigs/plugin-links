<script lang="ts" setup>
import { Toast, VButton, VModal, VSpace } from "@halo-dev/components";
import { computed, ref, watch } from "vue";
import type { Link } from "@/types";
import apiClient from "@/utils/api-client";
import cloneDeep from "lodash.clonedeep";
import { useRouteQuery } from "@vueuse/router";
import FormKit from "@/components/FormKit.vue";

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

const groupQuery = useRouteQuery<string>("group");

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

const handleSaveLink = async () => {
  try {
    saving.value = true;
    if (isUpdateMode.value) {
      await apiClient.put<Link>(
        `/apis/core.halo.run/v1alpha1/links/${formState.value.metadata.name}`,
        formState.value
      );
    } else {
      formState.value.spec.groupName = groupQuery.value;
      await apiClient.post<Link>(
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

const handleChooseFileButtonClick = () => {
  // 点击“选择文件”按钮时触发
  const input = $refs.fileInput as HTMLInputElement;
  input.click();
};

const handleFileInputChange = async (event: Event) => {
  // 选择文件后，处理文件上传
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) {
    return;
  }
  // 处理上传文件的逻辑
  const formData = new FormData();
  formData.append("file", file);
  const response = await apiClient.post("/upload", formData, {
    headers: { "Content-Type": "multipart/form-data" },
  });
  if (response.status === 200) {
    formState.value.spec.logo = response.data.url;
  } else {
    Toast.error("上传失败");
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

    <div>
      <FormKit
        v-if="formVisible"
        id="link-form"
        v-model="formState.spec"
        name="link-form"
        type="form"
        :config="{ validationVisibility: 'submit' }"
        @submit="handleSaveLink"
      >
        <FormKit
          type="text"
          name="displayName"
          validation="required"
          label="网站名称"
        ></FormKit>
        <FormKit
          type="url"
          name="url"
          validation="required"
          label="网站地址"
        ></FormKit>
        <FormKit type="text" name="logo" label="Logo">
          <input
            type="file"
            style="display: none"
            ref="fileInput"
            @change="handleFileInputChange"
          />
          <div>
            <VButton type="secondary" @click="handleChooseFileButtonClick"
              >选择文件</VButton
            >
            <span>{{ formState.spec.logo }}</span>
          </div>
        </FormKit>
        <FormKit type="textarea" name="description" label="描述"></FormKit>
      </FormKit>
    </div>

    <template #footer>
      <VSpace>
        <VButton
          :loading="saving"
          type="secondary"
          @click="$formkit.submit('link-form')"
          >提交</VButton
        >
        <VButton @click="onVisibleChange(false)">取消</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
