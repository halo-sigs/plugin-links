import "./styles/tailwind.css";
import type { PagesPublicState } from "@halo-dev/admin-shared";
import { BasicLayout } from "@halo-dev/admin-shared";
import { definePlugin } from "@halo-dev/admin-shared";
import LinkList from "@/views/LinkList.vue";
import type { Ref } from "vue";

export default definePlugin({
  name: "PluginLinks",
  components: [],
  routes: [
    {
      parentName: "BasePages",
      route: {
        path: "functional/links",
        component: BasicLayout,
        children: [
          {
            path: "",
            name: "Links",
            component: LinkList,
            meta: {
              permissions: ["plugin:links:view"],
            },
          },
        ],
      },
    },
  ],
  extensionPoints: {
    PAGES: (state: Ref<PagesPublicState>) => {
      state.value.functionalPages.push({
        name: "链接",
        url: "/links",
        path: "/pages/functional/links",
        permissions: ["plugin:links:view"],
      });
    },
  },
});
