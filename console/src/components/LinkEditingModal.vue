<script lang="ts" setup>
import { IconSave, VButton, VModal } from "@halo-dev/components";
import { computed, ref, watch } from "vue";
import type { Link } from "@/types";
import apiClient from "@/utils/api-client";
import cloneDeep from "lodash.clonedeep";
import { useRouteQuery } from "@vueuse/router";

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

const groupQuery = useRouteQuery<string>("group");

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
      <FormKit type="text" name="logo" label="Logo"></FormKit>
      <FormKit type="textarea" name="description" label="描述"></FormKit>
    </FormKit>
    <template #footer>
      <VButton
        :loading="saving"
        type="secondary"
        @click="$formkit.submit('link-form')"
      >
        <template #icon>
          <IconSave class="links-h-full links-w-full" />
        </template>
        保存
      </VButton>
    </template>
  </VModal>
</template>
