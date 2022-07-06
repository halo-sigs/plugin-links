<script lang="ts" name="LinkCreationModal" setup>
import { IconSave, VButton, VModal } from "@halo-dev/components";
import type { PropType } from "vue";
import { computed, ref, watch } from "vue";
import type { Link } from "@/types/extension";
import { axiosInstance } from "@halo-dev/admin-shared";

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
</script>
<template>
  <VModal
    :title="createModalTitle"
    :visible="visible"
    :width="650"
    @update:visible="handleVisibleChange"
  >
    <FormKit
      id="link-form"
      v-model="createForm.link.spec"
      :actions="false"
      type="form"
      @submit="handleCreateLink"
    >
      <FormKit
        label="网站名称"
        name="displayName"
        type="text"
        validation="required"
      ></FormKit>
      <FormKit
        label="网站地址"
        name="url"
        type="url"
        validation="required"
      ></FormKit>
      <FormKit label="Logo" name="logo" type="text"></FormKit>
      <FormKit label="描述" name="description" type="textarea"></FormKit>
    </FormKit>
    <template #footer>
      <VButton
        :loading="createForm.saving"
        type="secondary"
        @click="$formkit.submit('link-form')"
      >
        <template #icon>
          <IconSave class="h-full w-full" />
        </template>
        保存
      </VButton>
    </template>
  </VModal>
</template>
