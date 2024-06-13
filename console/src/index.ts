import "./styles/tailwind.css";
import "./styles/index.css";
import {
  definePlugin,
  type CommentSubjectRefProvider,
  type CommentSubjectRefResult,
} from "@halo-dev/console-shared";
import LinkList from "@/views/LinkList.vue";
import { markRaw } from "vue";
import RiLinksLine from "~icons/ri/links-line";
import type { Extension } from "@halo-dev/api-client/index";

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
  extensionPoints: {
    "comment:subject-ref:create": (): CommentSubjectRefProvider[] => {
      return [
        {
          kind: "Plugin",
          group: "plugin.halo.run",
          resolve: (subject: Extension): CommentSubjectRefResult => {
            return {
              label: "友链",
              title: "友链页面",
              externalUrl: "/links",
              route: {
                name: "Links",
              },
            };
          },
        },
      ];
    },
  },
});
