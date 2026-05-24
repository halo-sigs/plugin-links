export type LinkFeedRefreshState = "success" | "partial" | "failed";

export interface LinkFeedRefreshResultLike {
  partialFailure?: boolean;
  feeds?: Array<{
    error?: string;
  }>;
}

export function classifyLinkFeedRefreshResult(result: LinkFeedRefreshResultLike): LinkFeedRefreshState {
  const feeds = result.feeds || [];
  const hasFailure = feeds.some((feed) => !!feed.error);
  const hasSuccess = feeds.some((feed) => !feed.error);

  if (hasFailure && !hasSuccess) {
    return "failed";
  }
  if (result.partialFailure || (hasFailure && hasSuccess)) {
    return "partial";
  }
  return "success";
}
