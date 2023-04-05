import "./styles/tailwind.css";
import "./styles/index.css";
import { definePlugin } from "@halo-dev/console-shared";
import LinkList from "@/views/LinkList.vue";
import { markRaw } from "vue";
import RiLinksLine from "~icons/ri/links-line";

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: "Root",
      route: {
        path: "/links",
        name: "Links",
        component: LinkList,
        meta: {
          permissions: ["plugin:links:view"],
          menu: {
            name: "链接",
            group: "content",
            icon: markRaw(RiLinksLine),
          },
        },
      },
    },
  ],
});
