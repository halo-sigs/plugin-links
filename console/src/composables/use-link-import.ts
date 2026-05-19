import { linksConsoleApiClient } from "@/api";
import { computed, ref, shallowRef } from "vue";

export type LinkImportStep = "paste" | "preview";
export type ParsedItemStatus = "manual" | "scraping" | "success" | "failed";
export type ParsedItemStatusTone = "manual" | "scraping" | "success" | "error";

export interface ParsedItem {
  id: number;
  url: string;
  displayName: string;
  description: string;
  logo: string;
  status: ParsedItemStatus;
  checked: boolean;
}

export interface LinkImportSubmitData {
  items: ParsedItem[];
  groupName: string;
}

interface ValidationOptions {
  requireDisplayName?: boolean;
}

interface UseLinkImportOptions {
  initialGroupName?: string;
}

const SCRAPE_CONCURRENCY = 3;

export function isHttpUrl(value: string) {
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}

export function getParsedItemErrorMessage(item: ParsedItem, { requireDisplayName = true }: ValidationOptions = {}) {
  if (!item.url.trim()) {
    return "URL 不能为空";
  }
  if (!isHttpUrl(item.url.trim())) {
    return "URL 格式不正确";
  }
  if (requireDisplayName && !item.displayName.trim()) {
    return "名称不能为空";
  }
  return "";
}

export function getParsedItemStatusMeta(item: ParsedItem): { tone: ParsedItemStatusTone; label: string } {
  if (item.status === "scraping") {
    return { tone: "scraping", label: "解析中..." };
  }

  const errorMessage = getParsedItemErrorMessage(item);
  if (errorMessage) {
    if (item.status === "failed" && errorMessage === "名称不能为空") {
      return { tone: "error", label: "获取链接详情失败，请手动补充名称" };
    }
    return { tone: "error", label: errorMessage };
  }

  if (item.status === "success") {
    return { tone: "success", label: "已解析" };
  }

  if (item.status === "failed") {
    return { tone: "error", label: "解析失败，可手动补充" };
  }

  return { tone: "manual", label: "手动" };
}

export function normalizeParsedItem(item: ParsedItem): ParsedItem {
  return {
    ...item,
    url: item.url.trim(),
    displayName: item.displayName.trim(),
    description: item.description.trim(),
    logo: item.logo.trim(),
  };
}

export function useLinkImport(options: UseLinkImportOptions = {}) {
  const step = shallowRef<LinkImportStep>("paste");
  const rawText = shallowRef("");
  const selectedGroupName = shallowRef(options.initialGroupName || "");
  const enableScraping = shallowRef(false);
  const parsedItems = ref<ParsedItem[]>([]);
  const isParsing = shallowRef(false);

  const previewSummary = computed(() => {
    let selected = 0;
    let scraped = 0;
    let manual = 0;
    let failed = 0;

    for (const item of parsedItems.value) {
      if (item.checked) {
        selected++;
      }

      const { tone } = getParsedItemStatusMeta(item);
      if (tone === "success") {
        scraped++;
      } else if (tone === "error") {
        failed++;
      } else if (tone === "manual") {
        manual++;
      }
    }

    return {
      total: parsedItems.value.length,
      selected,
      scraped,
      manual,
      failed,
    };
  });

  const importableCount = computed(() => getImportableItems().length);

  function parseLines() {
    const lines = rawText.value
      .split("\n")
      .map((line) => line.trim())
      .filter((line) => line.length > 0);

    parsedItems.value = lines.map((line, index) => {
      const segments = line.split("|").map((segment) => segment.trim());

      return {
        id: index,
        url: segments[0] || "",
        displayName: segments[1] || "",
        description: segments[2] || "",
        logo: segments[3] || "",
        status: "manual",
        checked: true,
      };
    });
  }

  async function scrapeItems() {
    const itemsToScrape = parsedItems.value.filter(
      (item) =>
        item.checked &&
        !getParsedItemErrorMessage(item, { requireDisplayName: false }) &&
        (!item.displayName || !item.description || !item.logo),
    );

    if (itemsToScrape.length === 0) {
      return;
    }

    const queue = [...itemsToScrape];

    async function worker() {
      while (queue.length > 0) {
        const item = queue.shift();
        if (!item) {
          return;
        }

        item.status = "scraping";
        try {
          const { data: detail, status } = await linksConsoleApiClient.link.getLinkDetail(
            {
              url: item.url,
            },
            {
              validateStatus: () => true,
            },
          );
          if (status < 200 || status >= 300) {
            item.status = "failed";
            continue;
          }
          item.displayName = item.displayName || detail.title || "";
          item.description = item.description || detail.description || "";
          item.logo = item.logo || detail.icon || "";
          item.status = "success";
        } catch {
          item.status = "failed";
        }
      }
    }

    const workers = Array.from({ length: Math.min(SCRAPE_CONCURRENCY, itemsToScrape.length) }, () => worker());
    await Promise.all(workers);
  }

  async function parseInput() {
    if (!rawText.value.trim()) {
      return false;
    }

    parseLines();

    if (enableScraping.value) {
      isParsing.value = true;
      try {
        await scrapeItems();
      } finally {
        isParsing.value = false;
      }
    }

    step.value = "preview";
    return true;
  }

  function backToPaste() {
    step.value = "paste";
  }

  function getImportableItems() {
    return parsedItems.value
      .filter((item) => item.checked && !getParsedItemErrorMessage(item))
      .map((item) => normalizeParsedItem(item));
  }

  return {
    step,
    rawText,
    selectedGroupName,
    enableScraping,
    parsedItems,
    isParsing,
    previewSummary,
    importableCount,
    parseInput,
    backToPaste,
    getImportableItems,
  };
}
