import "./styles/tailwind.css";
import type { PagesPublicState } from "@halo-dev/admin-shared";
import { BasicLayout, definePlugin } from "@halo-dev/admin-shared";
import LinkList from "@/views/LinkList.vue";
import type { Ref } from "vue";

export default definePlugin({
  name: "PluginLinks",
  components: [],
  routes: [
    {
      path: "/pages/functional/links",
      component: BasicLayout,
      children: [
        {
          path: "",
          name: "Links",
          // eslint-disable-next-line @typescript-eslint/ban-ts-comment
          // @ts-ignore
          component: LinkList,
        },
      ],
      meta: {
        permissions: ["plugin:links:view"],
      },
    },
  ],
  extensionPoints: {
    PAGES: (state: Ref<PagesPublicState>) => {
      state.value.functionalPages.push({
        name: "友情链接",
        url: "/links",
        path: "/pages/functional/links",
        permissions: ["plugin:links:view"],
      });
    },
  },
});
