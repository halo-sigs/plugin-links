import { rsbuildConfig } from "@halo-dev/ui-plugin-bundler-kit";
import Icons from "unplugin-icons/rspack";
import { UnoCSSRspackPlugin } from "@unocss/webpack/rspack";

export default rsbuildConfig({
  rsbuild: {
    tools: {
      rspack: {
        plugins: [Icons({ compiler: "vue3" }), UnoCSSRspackPlugin()],
      },
    },
  },
});
