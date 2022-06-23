import { definePlugin } from "@halo-dev/admin-shared";
import type { PagesPublicState } from "@halo-dev/admin-shared";
import LinkList from "@/views/LinkList.vue";
import type { Ref } from "vue";

export default definePlugin({
  name: "PluginLinks",
  components: [],
  routes: [
    {
      path: "/functional/links",
      name: "Links",
      component: LinkList,
    },
  ],
  extensionPoints: {
    PAGES: (state: Ref<PagesPublicState>) => {
      state.value.functionalPages.push({
        name: "友情链接",
        url: "/links",
        path: "/functional/links",
      });
    },
  },
});
