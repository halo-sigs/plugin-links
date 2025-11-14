import { definePlugin, type CommentSubjectRefProvider, type CommentSubjectRefResult } from "@halo-dev/ui-shared";
import "uno.css";
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
        component: () => import("@/views/LinkList.vue"),
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
          resolve: (): CommentSubjectRefResult => {
            return {
              label: "链接",
              title: "链接页面",
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
