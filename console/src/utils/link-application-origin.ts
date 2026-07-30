import type { LinkAiFeatureStatus, LinkApplication, OriginTypeEnum, Ref } from "@/api/generated";
import type { RouteLocationRaw } from "vue-router";

export interface LinkApplicationSourceMeta {
  label: string;
  tagType: "default" | "primary" | "success";
}

export interface LinkApplicationSubjectMeta {
  label: string;
  route?: RouteLocationRaw;
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

export function linkApplicationSubjectMeta(subjectRef: Ref | undefined): LinkApplicationSubjectMeta | undefined {
  if (!subjectRef) {
    return undefined;
  }

  if (subjectRef.group === "content.halo.run" && subjectRef.kind === "Post") {
    return {
      label: `文章 · ${subjectRef.name}`,
      route: {
        name: "PostEditor",
        query: {
          name: subjectRef.name,
        },
      },
    };
  }

  if (subjectRef.group === "content.halo.run" && subjectRef.kind === "SinglePage") {
    return {
      label: `页面 · ${subjectRef.name}`,
      route: {
        name: "SinglePageEditor",
        query: {
          name: subjectRef.name,
        },
      },
    };
  }

  if (subjectRef.group === "plugin.halo.run" && subjectRef.kind === "Plugin") {
    return {
      label: "链接页面",
      route: {
        name: "Links",
      },
    };
  }

  return {
    label: `${subjectRef.kind} · ${subjectRef.name}`,
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
