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
  ],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  define: {
    "process.env": process.env,
  },
  build: {
    outDir: "build/dist",
    emptyOutDir: true,
    lib: {
      entry: "src/index.ts",
      name: "PluginLinks",
      formats: ["iife"],
      fileName: () => "main.js",
    },
    rollupOptions: {
      external: [
        "vue",
        "vue-router",
        "@vueuse/core",
        "@vueuse/components",
        "@vueuse/router",
        "@halo-dev/shared",
        "@halo-dev/components",
        "@halo-dev/api-client",
        "@halo-dev/richtext-editor",
        "axios",
      ],
      output: {
        globals: {
          vue: "Vue",
          "vue-router": "VueRouter",
          "@vueuse/core": "VueUse",
          "@vueuse/components": "VueUse",
          "@vueuse/router": "VueUse",
          "@halo-dev/console-shared": "HaloConsoleShared",
          "@halo-dev/components": "HaloComponents",
          "@halo-dev/api-client": "HaloApiClient",
          "@halo-dev/richtext-editor": "RichTextEditor",
          axios: "axios",
        },
        extend: true,
      },
    },
  },
});
