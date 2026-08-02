import { pluginVue } from "@rsbuild/plugin-vue";
import { defineConfig } from "@rstest/core";
import Icons from "unplugin-icons/rspack";

export default defineConfig({
  testEnvironment: "happy-dom",
  clearMocks: true,
  plugins: [pluginVue()],
  tools: {
    rspack: {
      plugins: [Icons({ compiler: "vue3" })],
    },
  },
});
