import type { LinkCommentSummaryDTO } from "@/api/generated";

export function commentPlainText(comment: Pick<LinkCommentSummaryDTO, "content" | "raw">) {
  return htmlToPlainText(comment.raw || comment.content || "");
}

export function htmlToPlainText(value: string) {
  const htmlWithBreaks = value
    .replace(/<\s*br\s*\/?>/gi, "\n")
    .replace(/<\/\s*(blockquote|div|h[1-6]|li|p|tr)\s*>/gi, "\n");

  const text =
    typeof DOMParser === "undefined"
      ? htmlWithBreaks.replace(/<[^>]*>/g, "")
      : new DOMParser().parseFromString(htmlWithBreaks, "text/html").body.textContent || "";

  return text
    .replace(/[ \t\f\v]+/g, " ")
    .replace(/\n[ \t]+/g, "\n")
    .replace(/[ \t]+\n/g, "\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}
