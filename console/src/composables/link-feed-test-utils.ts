import { QueryClient, VueQueryPlugin } from "@tanstack/vue-query";
import { createApp, effectScope, type EffectScope } from "vue";
import { createMemoryHistory, createRouter, type Router } from "vue-router";

export function createFeedTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });
}

export interface FeedComposableTestContext<T> {
  result: T;
  queryClient: QueryClient;
  router: Router;
  scope: EffectScope;
}

export function runWithFeedTestApp<T>(
  run: () => T,
  queryClient: QueryClient = createFeedTestQueryClient(),
): FeedComposableTestContext<T> {
  const app = createApp({ template: "<div />" });
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: "/:pathMatch(.*)*", component: { template: "<div />" } }],
  });
  app.use(router);
  app.use(VueQueryPlugin, { queryClient });
  const scope = effectScope();
  const result = app.runWithContext(() => scope.run(run)) as T;
  return { result, queryClient, router, scope };
}
