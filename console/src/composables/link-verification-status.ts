import type { Link } from "@/api/generated";

export type LinkVerificationTone = "danger" | "muted" | "success" | "warning";

export interface LinkVerificationStatusMeta {
  description: string;
  label: string;
  tone: LinkVerificationTone;
}

export function accessVerificationStatusMeta(link: Link): LinkVerificationStatusMeta {
  const access = link.status?.verification?.access;
  switch (access?.state) {
    case "CHECKING":
      return {
        tone: "warning",
        label: "访问检测中",
        description: "正在检测链接访问状态",
      };
    case "ACCESSIBLE":
      return {
        tone: "success",
        label: "访问正常",
        description: access.statusCode ? `访问正常，HTTP ${access.statusCode}` : "访问正常",
      };
    case "INACCESSIBLE":
      return {
        tone: "danger",
        label: "访问异常",
        description: access.error || (access.statusCode ? `访问异常，HTTP ${access.statusCode}` : "访问异常"),
      };
    default:
      return {
        tone: "muted",
        label: "访问未检测",
        description: "尚未检测链接访问状态",
      };
  }
}

export function backlinkVerificationStatusMeta(link: Link): LinkVerificationStatusMeta {
  const backlink = link.status?.verification?.backlink;
  switch (backlink?.state) {
    case "CHECKING":
      return {
        tone: "warning",
        label: "反链检测中",
        description: "正在扫描反链页面",
      };
    case "FOUND":
      return {
        tone: "success",
        label: "反链正常",
        description: backlink.matchedUrl ? `已发现反链：${backlink.matchedUrl}` : "已发现反链",
      };
    case "MISSING":
      return {
        tone: "danger",
        label: "未发现反链",
        description: backlink.scanUrl ? `未在 ${backlink.scanUrl} 发现反链` : "未发现反链",
      };
    case "FAILED":
      return {
        tone: "danger",
        label: "反链检测失败",
        description: backlink.error || "反链检测失败",
      };
    case "NOT_CONFIGURED":
      return {
        tone: "muted",
        label: "未配置反链",
        description: "未配置反链检测页面",
      };
    default:
      return {
        tone: "muted",
        label: link.spec?.verification?.backlinkScanUrl ? "反链未检测" : "未配置反链",
        description: link.spec?.verification?.backlinkScanUrl ? "尚未检测反链状态" : "未配置反链检测页面",
      };
  }
}

export function isLinkVerificationChecking(link: Link) {
  const verification = link.status?.verification;
  return verification?.access?.state === "CHECKING" || verification?.backlink?.state === "CHECKING";
}

export function hasRunningLinkVerification(groups?: Array<{ links: Link[] }>) {
  return Boolean(groups?.some((group) => group.links.some(isLinkVerificationChecking)));
}
