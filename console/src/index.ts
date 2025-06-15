import { definePlugin, type CommentSubjectRefProvider, type CommentSubjectRefResult } from "@halo-dev/console-shared";
import { defineAsyncComponent, markRaw } from "vue";
import RiLinksLine from "~icons/ri/links-line";
import "uno.css";
import { VLoading } from "@halo-dev/components";

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: "Root",
      route: {
        path: "/links",
        name: "Links",
        component: defineAsyncComponent({
          loader: () => import("@/views/LinkList.vue"),
          loadingComponent: VLoading,
        }),
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
