<script lang="ts" name="LinkCreationModal" setup>
import {
  IconSave,
  VButton,
  VModal,
  VCodemirror,
  IconEye,
  IconCodeBoxLine,
} from "@halo-dev/components";
import { computed, ref, watch } from "vue";
import type { Link } from "@/types";
import apiClient from "@/utils/api-client";
import cloneDeep from "lodash.clonedeep";
import YAML from "yaml";

const props = defineProps<{
  visible: boolean;
  link: Link | null;
}>();

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

interface LinkEditingFormState {
  link: Link;
  saving: boolean;
  rawMode: boolean;
  raw: string;
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
  rawMode: false,
  raw: "",
};

const editingFormState = ref<LinkEditingFormState>(cloneDeep(initialFormState));

const isUpdateForm = computed(() => {
  return !!editingFormState.value.link.metadata.creationTimestamp;
});

const editingTitle = computed(() => {
  return isUpdateForm.value ? "编辑链接" : "添加链接";
});

const modalWidth = computed(() => {
  return editingFormState.value.rawMode ? 750 : 650;
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
    if (editingFormState.value.rawMode) {
      editingFormState.value.link = YAML.parse(editingFormState.value.raw);
    }

    editingFormState.value.saving = true;
    if (isUpdateForm.value) {
      const { data } = await apiClient.put<Link>(
        `/apis/core.halo.run/v1alpha1/links/${editingFormState.value.link.metadata.name}`,
        editingFormState.value.link
      );
      emit("saved", data);
    } else {
      const { data } = await apiClient.post<Link>(
        `/apis/core.halo.run/v1alpha1/links`,
        editingFormState.value.link
      );
      emit("saved", data);
    }
    handleVisibleChange(false);
  } catch (e) {
    console.error(e);
  } finally {
    editingFormState.value.saving = false;
  }
};

const handleRawModeChange = () => {
  editingFormState.value.rawMode = !editingFormState.value.rawMode;

  if (editingFormState.value.rawMode) {
    editingFormState.value.raw = YAML.stringify(editingFormState.value.link);
  } else {
    editingFormState.value.link = YAML.parse(editingFormState.value.raw);
  }
};
</script>
<template>
  <VModal
    :title="editingTitle"
    :visible="visible"
    :width="modalWidth"
    @update:visible="handleVisibleChange"
  >
    <template #actions>
      <div class="modal-header-action" @click="handleRawModeChange">
        <IconCodeBoxLine v-if="!editingFormState.rawMode" />
        <IconEye v-else />
      </div>
    </template>

    <VCodemirror
      v-show="editingFormState.rawMode"
      v-model="editingFormState.raw"
      height="50vh"
      language="yaml"
    />

    <div v-show="!editingFormState.rawMode">
      <FormKit
        id="link-form"
        v-model="editingFormState.link.spec"
        :actions="false"
        type="form"
        @submit="handleSaveLink"
      >
        <FormKitSchema :schema="formSchema" />
      </FormKit>
    </div>
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
