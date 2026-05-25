import { definePlugin, type CommentSubjectRefProvider, type CommentSubjectRefResult } from "@halo-dev/ui-shared";
import "uno.css";
import { markRaw } from "vue";
import Rss2FillIcon from "~icons/mingcute/rss-2-fill";
import RiLinksLine from "~icons/ri/links-line";

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: "Root",
      route: {
        path: "/links",
        name: "Links",
        component: () => import(/* webpackChunkName: "links-list" */ "@/views/LinkList.vue"),
        meta: {
          permissions: ["plugin:links:view"],
          title: "链接",
          menu: {
            name: "链接",
            group: "content",
            icon: markRaw(RiLinksLine),
            priority: 50,
          },
        },
      },
    },
    {
      parentName: "Root",
      route: {
        path: "/links/rss",
        name: "LinkFeedItems",
        component: () => import(/* webpackChunkName: "link-feed-list" */ "@/views/LinkFeedList.vue"),
        meta: {
          permissions: ["plugin:links:view"],
          title: "链接订阅",
          hideFooter: true,
          menu: {
            name: "订阅",
            group: "content",
            icon: markRaw(Rss2FillIcon),
            priority: 51,
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
