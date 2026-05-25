import { linksConsoleApiClient } from "@/api";
import { Toast } from "@halo-dev/components";
import { QK_LINK_FEED_ITEMS } from "./use-link-feed";
import { QK_GROUPS_WITH_LINKS, QK_RSS_GROUPS_WITH_LINKS } from "./use-link-fetch";

export interface InitialLinkFeedRefreshApi {
  refreshLinkFeed(request: { name: string }): Promise<unknown>;
}

export interface InitialLinkFeedRefreshQueryClient {
  invalidateQueries(options: { queryKey: unknown[] }): Promise<unknown> | unknown;
}

export interface InitialLinkFeedRefreshToast {
  success(message: string): void;
}

export interface InitialLinkFeedRefreshOptions {
  linkName?: string;
  queryClient: InitialLinkFeedRefreshQueryClient;
  feedApi?: InitialLinkFeedRefreshApi;
  toast?: InitialLinkFeedRefreshToast;
}

export function startInitialLinkFeedRefresh(options: InitialLinkFeedRefreshOptions) {
  if (!options.linkName) {
    return;
  }

  void refreshInitialLinkFeed(options);
}

export async function refreshInitialLinkFeed({
  linkName,
  queryClient,
  feedApi = linksConsoleApiClient.feed,
  toast = Toast,
}: InitialLinkFeedRefreshOptions) {
  if (!linkName) {
    return;
  }

  try {
    await feedApi.refreshLinkFeed({ name: linkName });
    toast.success("RSS 已自动获取");
  } catch {
    // Halo's API interceptor shows request failure toasts.
  } finally {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: [QK_GROUPS_WITH_LINKS] }),
      queryClient.invalidateQueries({ queryKey: [QK_RSS_GROUPS_WITH_LINKS] }),
      queryClient.invalidateQueries({ queryKey: [QK_LINK_FEED_ITEMS] }),
    ]);
  }
}
