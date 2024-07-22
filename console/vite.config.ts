import { HaloUIPluginBundlerKit } from "@halo-dev/ui-plugin-bundler-kit";
import Vue from "@vitejs/plugin-vue";
import UnoCSS from "unocss/vite";
import Icons from "unplugin-icons/vite";
import { fileURLToPath, URL } from "url";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [
    Vue(),
    Icons({ compiler: "vue3" }),
    UnoCSS({
      mode: "vue-scoped",
      configFile: "./uno.config.ts",
    }),
    HaloUIPluginBundlerKit(),
  ],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
});
