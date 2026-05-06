import { rsbuildConfig } from "@halo-dev/ui-plugin-bundler-kit";
import { UnoCSSRspackPlugin } from "@unocss/webpack/rspack";
import Icons from "unplugin-icons/rspack";

export default rsbuildConfig({
  rsbuild: {
    tools: {
      rspack: {
        cache: false,
        plugins: [Icons({ compiler: "vue3" }), UnoCSSRspackPlugin()],
      },
    },
  },
});
