import type {
  LinkAiFeatureStatus,
  LinkApplication,
  LinkApplicationOriginSubject,
  OriginTypeEnum,
} from "@/api/generated";
import type { RouteLocationRaw } from "vue-router";

export interface LinkApplicationSourceMeta {
  label: string;
  tagType: "default" | "primary" | "success";
}

export interface LinkApplicationSubjectMeta {
  label: string;
  url?: string;
}

const linkApplicationSourceMetas: Record<OriginTypeEnum, LinkApplicationSourceMeta> = {
  FORM: {
    label: "表单申请",
    tagType: "primary",
  },
  COMMENT: {
    label: "评论识别",
    tagType: "success",
  },
};

export function linkApplicationSourceMeta(application: LinkApplication): LinkApplicationSourceMeta {
  return linkApplicationSourceMetas[application.spec.origin.type];
}

export function linkApplicationSubjectMeta(
  subject: LinkApplicationOriginSubject | undefined,
): LinkApplicationSubjectMeta | undefined {
  if (!subject) {
    return undefined;
  }

  const title = subject.title?.trim();
  const kindName = subject.kindName?.trim();

  return {
    label: title ? (kindName ? `${kindName} · ${title}` : title) : kindName || "评论来源",
    url: subject.url?.trim() || undefined,
  };
}

export function linkApplicationCommentRoute(application: LinkApplication): RouteLocationRaw | undefined {
  const commentName = application.spec.origin.comment?.name;
  if (!commentName) {
    return undefined;
  }
  return {
    name: "Comments",
  };
}

export function isCommentRecognitionUnavailable(status: LinkAiFeatureStatus | undefined): boolean {
  return (
    status?.commentApplicationRecognitionEnabled === true && status.commentApplicationRecognitionOperational !== true
  );
}
