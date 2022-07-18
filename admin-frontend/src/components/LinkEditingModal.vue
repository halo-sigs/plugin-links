<script lang="ts" name="LinkCreationModal" setup>
import { IconSave, VButton, VModal } from "@halo-dev/components";
import { computed, ref, watch } from "vue";
import type { Link } from "@halo-dev/api-client";
import { apiClient } from "@halo-dev/admin-shared";
import cloneDeep from "lodash.clonedeep";

const props = defineProps<{
  visible: boolean;
  link: Link | null;
}>();

const emit = defineEmits<{
  (event: "update:visible", value: boolean): void;
  (event: "close"): void;
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

interface LinkEditingFormState {
  link: Link;
  saving: boolean;
}

const initialFormState: LinkEditingFormState = {
  link: {
    metadata: {
      name: Math.random().toString(),
    },
    spec: {
      displayName: "",
      url: "",
      logo: "",
    },
    kind: "Link",
    apiVersion: "core.halo.run/v1alpha1",
  },
  saving: false,
};

const editingFormState = ref<LinkEditingFormState>(cloneDeep(initialFormState));

const isUpdateForm = computed(() => {
  return !!editingFormState.value.link.metadata.creationTimestamp;
});

const editingTitle = computed(() => {
  return isUpdateForm.value ? "编辑链接" : "添加链接";
});

watch(props, (newVal) => {
  if (newVal.visible && props.link) {
    editingFormState.value.link = cloneDeep(props.link);
    return;
  }
  editingFormState.value = cloneDeep(initialFormState);
});

const handleVisibleChange = (visible: boolean) => {
  emit("update:visible", visible);
  if (!visible) {
    emit("close");
  }
};

const handleSaveLink = async () => {
  try {
    editingFormState.value.saving = true;
    if (isUpdateForm.value) {
      await apiClient.extension.link.updatecoreHaloRunV1alpha1Link(
        editingFormState.value.link.metadata.name,
        editingFormState.value.link
      );
    } else {
      await apiClient.extension.link.createcoreHaloRunV1alpha1Link(
        editingFormState.value.link
      );
    }
    handleVisibleChange(false);
  } catch (e) {
    console.error(e);
  } finally {
    editingFormState.value.saving = false;
  }
};
</script>
<template>
  <VModal
    :title="editingTitle"
    :visible="visible"
    :width="650"
    @update:visible="handleVisibleChange"
  >
    <FormKit
      id="link-form"
      v-model="editingFormState.link.spec"
      :actions="false"
      type="form"
      @submit="handleSaveLink"
    >
      <FormKitSchema :schema="formSchema" />
    </FormKit>
    <template #footer>
      <VButton
        :loading="editingFormState.saving"
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
