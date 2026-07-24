/// <reference types="@rsbuild/core" />
/// <reference types="unplugin-icons/types/vue" />

import "vue";

declare module "vue" {
  interface ComponentCustomProperties {
    $formkit: {
      submit(formId: string): void;
    };
  }
}
