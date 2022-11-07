<script lang="ts" setup>
import { IconSave, VButton, VModal } from "@halo-dev/components";
import { computed, defineProps, ref, watch } from "vue";
import type { Link } from "@/types";
import apiClient from "@/utils/api-client";
import cloneDeep from "lodash.clonedeep";
import { v4 as uuid } from "uuid";
import { reset, submitForm } from "@formkit/core";

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
  (event: "saved", link: Link): void;
}>();

const formSchema = [
  {
    $formkit: "text",
    name: "displayName",
    label: "网站名称",
    validation: "required",
  },
  {
    $formkit: "url",
    name: "url",
    label: "网站地址",
    validation: "required",
  },
  {
    $formkit: "text",
    name: "logo",
    label: "Logo",
  },
  {
    $formkit: "textarea",
    name: "description",
    label: "描述",
  },
];

const initialFormState: Link = {
  metadata: {
    name: uuid(),
  },
  spec: {
    displayName: "",
    url: "",
    logo: "",
  },
  kind: "Link",
  apiVersion: "core.halo.run/v1alpha1",
};

const formState = ref<Link>(cloneDeep(initialFormState));
const saving = ref<boolean>(false);

const isUpdateMode = computed(() => {
  return !!formState.value.metadata.creationTimestamp;
});

const modalTitle = computed(() => {
  return isUpdateMode.value ? "编辑链接" : "添加链接";
});

const onVisibleChange = (visible: boolean) => {
  emit("update:visible", visible);
  if (!visible) {
    emit("close");
  }
};

const handleResetForm = () => {
  formState.value = cloneDeep(initialFormState);
  formState.value.metadata.name = uuid();
  reset("link-form");
};

watch(
  () => props.visible,
  (visible) => {
    if (!visible) {
      handleResetForm();
    }
  }
);

watch(
  () => props.link,
  (link) => {
    if (link) {
      formState.value = cloneDeep(link);
    } else {
      handleResetForm();
    }
  }
);

const handleSaveLink = async () => {
  try {
    saving.value = true;
    if (isUpdateMode.value) {
      const { data } = await apiClient.put<Link>(
        `/apis/core.halo.run/v1alpha1/links/${formState.value.metadata.name}`,
        formState.value
      );
      emit("saved", data);
    } else {
      const { data } = await apiClient.post<Link>(
        `/apis/core.halo.run/v1alpha1/links`,
        formState.value
      );
      emit("saved", data);
    }
    onVisibleChange(false);
  } catch (e) {
    console.error(e);
  } finally {
    saving.value = false;
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
      id="link-form"
      v-model="formState.spec"
      :actions="false"
      type="form"
      @submit="handleSaveLink"
    >
      <FormKitSchema :schema="formSchema" />
    </FormKit>
    <template #footer>
      <VButton
        :loading="saving"
        type="secondary"
        @click="submitForm('link-form')"
      >
        <template #icon>
          <IconSave class="links-h-full links-w-full" />
        </template>
        保存
      </VButton>
    </template>
  </VModal>
</template>
