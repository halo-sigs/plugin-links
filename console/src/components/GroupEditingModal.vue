<script lang="ts" setup>
import { Toast, VButton, VModal, VSpace } from "@halo-dev/components";
import { computed, ref, watch } from "vue";
import apiClient from "@/utils/api-client";
import cloneDeep from "lodash.clonedeep";
import type { LinkGroup } from "@/types";

const props = withDefaults(
  defineProps<{
    visible: boolean;
    group?: LinkGroup;
  }>(),
  {
    visible: false,
    group: undefined,
  }
);

const emit = defineEmits<{
  (event: "update:visible", visible: boolean): void;
  (event: "close"): void;
}>();

const initialFormState: LinkGroup = {
  apiVersion: "core.halo.run/v1alpha1",
  kind: "LinkGroup",
  metadata: {
    name: "",
    generateName: "link-group-",
  },
  spec: {
    displayName: "",
    priority: 0,
    links: [],
  },
};

const formState = ref<LinkGroup>(cloneDeep(initialFormState));
const saving = ref(false);
const formVisible = ref(false);

const isUpdateMode = computed(() => {
  return !!formState.value.metadata.creationTimestamp;
});

const modalTitle = computed(() => {
  return isUpdateMode.value ? "编辑分组" : "新建分组";
});

const handleCreateOrUpdateGroup = async () => {
  try {
    saving.value = true;
    if (isUpdateMode.value) {
      await apiClient.put(
        `/apis/core.halo.run/v1alpha1/linkgroups/${formState.value.metadata.name}`,
        formState.value
      );
    } else {
      await apiClient.post(
        "/apis/core.halo.run/v1alpha1/linkgroups",
        formState.value
      );
    }

    Toast.success("保存成功");

    onVisibleChange(false);
  } catch (e) {
    console.error("Failed to create link group", e);
  } finally {
    saving.value = false;
  }
};

const onVisibleChange = (visible: boolean) => {
  emit("update:visible", visible);
  if (!visible) {
    emit("close");
  }
};

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      if (props.group) formState.value = cloneDeep(props.group);
      formVisible.value = true;
    } else {
      setTimeout(() => {
        formVisible.value = false;
        formState.value = cloneDeep(initialFormState);
      }, 200);
    }
  }
);
</script>
<template>
  <VModal
    :visible="visible"
    :width="500"
    :title="modalTitle"
    @update:visible="onVisibleChange"
  >
    <div>
      <FormKit
        v-if="formVisible"
        id="link-group-form"
        v-model="formState.spec"
        name="link-group-form"
        type="form"
        :config="{ validationVisibility: 'submit' }"
        @submit="handleCreateOrUpdateGroup"
      >
        <FormKit
          name="displayName"
          help="可根据此名称查询链接"
          label="分组名称"
          type="text"
          validation="required"
        ></FormKit>
      </FormKit>
    </div>
    <template #footer>
      <VSpace>
        <VButton
          :loading="saving"
          type="secondary"
          @click="$formkit.submit('link-group-form')"
        >
          提交
        </VButton>
        <VButton @click="onVisibleChange(false)"> 取消</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
