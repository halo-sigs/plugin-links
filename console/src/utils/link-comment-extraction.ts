import type { LinkCommentExtractionResult } from "@/api/generated";

interface LinkCommentExtractionTarget {
  url: string;
  displayName: string;
  logo?: string;
  description?: string;
  rss: {
    enabled: boolean;
    feedUrls: string[];
  };
}

export interface LinkCommentExtractionApplyResult {
  warnings: string[];
}

export function applyLinkCommentExtraction(
  target: LinkCommentExtractionTarget,
  extraction: LinkCommentExtractionResult,
): LinkCommentExtractionApplyResult {
  const warnings: string[] = [];

  if (isHttpUrl(extraction.url)) {
    target.url = extraction.url;
  } else if (extraction.url) {
    warnings.push("AI 提取的网站地址格式不正确，已忽略");
  }

  if (extraction.displayName) {
    target.displayName = extraction.displayName;
  }

  if (isHttpUrl(extraction.logo)) {
    target.logo = extraction.logo;
  } else if (extraction.logo) {
    warnings.push("AI 提取的 Logo 地址格式不正确，已忽略");
  }

  if (extraction.description) {
    target.description = extraction.description;
  }

  if (isHttpUrl(extraction.rssUrl)) {
    target.rss.enabled = true;
    target.rss.feedUrls = normalizeFeedUrls([...target.rss.feedUrls, extraction.rssUrl]);
  } else if (extraction.rssUrl) {
    warnings.push("AI 提取的 RSS 地址格式不正确，已忽略");
  }

  return { warnings };
}

function isHttpUrl(value: string | undefined | null): value is string {
  if (!value) {
    return false;
  }
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}

function normalizeFeedUrls(feedUrls: string[]) {
  return [...new Set(feedUrls.map((feedUrl) => feedUrl.trim()).filter(Boolean))];
}
