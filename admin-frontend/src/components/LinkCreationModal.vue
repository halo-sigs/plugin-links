<script lang="ts" setup name="LinkCreationModal">
import {
  IconEye,
  IconSave,
  VButton,
  VInput,
  VModal,
  VTextarea,
} from "@halo-dev/components";
import type { PropType } from "vue";
import { computed, ref, watch } from "vue";
import type { Link } from "@/types/extension";
import axiosInstance from "@/utils/api-client";
import { basicSetup, EditorView } from "codemirror";
import { StreamLanguage } from "@codemirror/language";
import { yaml as yamlLang } from "@codemirror/legacy-modes/mode/yaml";
import yaml from "yaml";
import { vim } from "@replit/codemirror-vim";

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  link: {
    type: Object as PropType<Link | null>,
    default: null,
  },
});

const emit = defineEmits(["update:visible", "close"]);

interface createFormState {
  link: Link;
  saving: boolean;
}

const createForm = ref<createFormState>({
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
});
const codeMode = ref(false);
const rawCode = ref("");
const codeEditorView = ref();

console.log(yaml.stringify(createForm.value.link));

const isUpdateMode = computed(() => {
  return !!createForm.value.link.metadata.creationTimestamp;
});

const createModalTitle = computed(() => {
  return isUpdateMode.value ? "编辑链接" : "添加链接";
});

watch(props, (newVal) => {
  if (newVal.visible && props.link) {
    createForm.value.link = props.link;
  }
});

const handleVisibleChange = (visible: boolean) => {
  emit("update:visible", visible);
  if (!visible) {
    emit("close");
  }
};

const handleCreateLink = async () => {
  try {
    createForm.value.saving = true;
    if (isUpdateMode.value) {
      await axiosInstance.put<Link>(
        `/apis/core.halo.run/v1alpha1/links/${createForm.value.link.metadata.name}`,
        createForm.value.link
      );
    } else {
      await axiosInstance.post<Link>(
        `/apis/core.halo.run/v1alpha1/links`,
        createForm.value.link
      );
    }
    handleVisibleChange(false);
  } catch (e) {
    console.error(e);
  } finally {
    createForm.value.saving = false;
  }
};

const handleOpenCodeMode = () => {
  codeMode.value = !codeMode.value;
  if (codeMode.value) {
    rawCode.value = yaml.stringify(createForm.value.link);
    handleCreateYamlEditor();
  } else {
    createForm.value.link = yaml.parse(rawCode.value);
  }
};

const yamlEditor = ref();
const handleCreateYamlEditor = () => {
  if (codeEditorView.value) {
    codeEditorView.value.destroy();
  }
  codeEditorView.value = new EditorView({
    doc: rawCode.value,
    extensions: [basicSetup, StreamLanguage.define(yamlLang), vim()],
    parent: yamlEditor.value,
  });
};
</script>
<template>
  <VModal
    @update:visible="handleVisibleChange"
    :visible="visible"
    :title="createModalTitle"
    :width="650"
  >
    <div class="w-full flex justify-end mb-2">
      <IconEye @click="handleOpenCodeMode" />
    </div>
    <form v-if="!codeMode">
      <div class="space-y-6 divide-y-0 sm:divide-y sm:divide-gray-200">
        <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:pt-5">
          <label
            class="block text-sm font-medium text-gray-700 sm:mt-px sm:pt-2"
          >
            网站名称
          </label>
          <div class="mt-1 sm:col-span-2 sm:mt-0">
            <VInput v-model="createForm.link.spec.displayName"></VInput>
          </div>
        </div>

        <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:pt-5">
          <label
            class="block text-sm font-medium text-gray-700 sm:mt-px sm:pt-2"
          >
            网站地址
          </label>
          <div class="mt-1 sm:col-span-2 sm:mt-0">
            <VInput v-model="createForm.link.spec.url"></VInput>
          </div>
        </div>

        <div class="sm:grid sm:grid-cols-3 sm:items-center sm:gap-4 sm:pt-5">
          <label class="block text-sm font-medium text-gray-700"> Logo </label>
          <div class="mt-1 sm:col-span-2 sm:mt-0">
            <VInput v-model="createForm.link.spec.logo"></VInput>
          </div>
        </div>

        <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:pt-5">
          <label
            class="block text-sm font-medium text-gray-700 sm:mt-px sm:pt-2"
          >
            描述
          </label>
          <div class="mt-1 sm:col-span-2 sm:mt-0">
            <VTextarea v-model="createForm.link.spec.description"></VTextarea>
          </div>
        </div>
      </div>
    </form>
    <form v-show="codeMode">
      <div ref="yamlEditor"></div>
    </form>
    <template #footer>
      <VButton
        type="secondary"
        :loading="createForm.saving"
        @click="handleCreateLink"
      >
        <template #icon>
          <IconSave class="w-full h-full" />
        </template>
        保存
      </VButton>
    </template>
  </VModal>
</template>
