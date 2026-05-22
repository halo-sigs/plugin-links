import { definePlugin, type CommentSubjectRefProvider, type CommentSubjectRefResult } from "@halo-dev/ui-shared";
import "uno.css";
import { markRaw } from "vue";
import MdiRss from "~icons/mdi/rss";
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
          title: "友链动态",
          menu: {
            name: "友链动态",
            group: "content",
            icon: markRaw(MdiRss),
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
