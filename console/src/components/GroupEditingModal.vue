<script lang="ts" setup>
import { linksCoreApiClient } from "@/api";
import { LinkGroup } from "@/api/generated";
import { Toast, VButton, VModal, VSpace } from "@halo-dev/components";
import { cloneDeep } from "es-toolkit";
import { computed, nextTick, onMounted, ref, useTemplateRef } from "vue";

const props = withDefaults(
  defineProps<{
    group?: LinkGroup;
  }>(),
  {
    group: undefined,
  }
);

const emit = defineEmits<{
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
    hidden: false,
  },
};

const formState = ref<LinkGroup>(cloneDeep(initialFormState));
const isSubmitting = ref(false);
const modal = useTemplateRef<InstanceType<typeof VModal> | null>("modal");

const isUpdateMode = computed(() => {
  return !!formState.value.metadata.creationTimestamp;
});

const modalTitle = computed(() => {
  return isUpdateMode.value ? "编辑分组" : "新建分组";
});

const annotationsFormRef = ref();

const handleCreateOrUpdateGroup = async () => {
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
    isSubmitting.value = true;
    if (isUpdateMode.value) {
      await linksCoreApiClient.group.updateLinkGroup({
        name: formState.value.metadata.name,
        linkGroup: formState.value,
      });
    } else {
      await linksCoreApiClient.group.createLinkGroup({
        linkGroup: formState.value,
      });
    }

    Toast.success("保存成功");

    modal.value?.close();
  } catch (e) {
    console.error("Failed to create link group", e);
  } finally {
    isSubmitting.value = false;
  }
};

onMounted(() => {
  if (props.group) {
    formState.value = cloneDeep(props.group);
  }
});
</script>
<template>
  <VModal ref="modal" :width="600" :title="modalTitle" @close="emit('close')">
    <FormKit
      id="link-group-form"
      v-model="formState.spec"
      name="link-group-form"
      type="form"
      :config="{ validationVisibility: 'submit' }"
      @submit="handleCreateOrUpdateGroup"
    >
      <div class=":uno: md:grid md:grid-cols-4 md:gap-6">
        <div class=":uno: md:col-span-1">
          <div class=":uno: sticky top-0">
            <span class=":uno: text-base text-gray-900 font-medium"> 常规 </span>
          </div>
        </div>
        <div class=":uno: mt-5 md:col-span-3 md:mt-0 divide-y divide-gray-100">
          <FormKit name="displayName" label="分组名称" type="text" validation="required"></FormKit>
          <FormKit name="hidden" label="隐藏分组" type="checkbox"></FormKit>
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
          kind="LinkGroup"
          group="core.halo.run"
        />
      </div>
    </div>

    <template #footer>
      <VSpace>
        <!-- @vue-ignore -->
        <VButton :loading="isSubmitting" type="secondary" @click="$formkit.submit('link-group-form')"> 提交 </VButton>
        <VButton @click="modal?.close()"> 取消</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
