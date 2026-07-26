import type { ApprovalRequest, LinkApplication } from "@/api/generated";
import { describe, expect, it } from "@rstest/core";
import {
  buildLinkApplicationApprovalRequest,
  buildLinkApplicationCleanupParams,
  buildLinkApplicationQuery,
  canVerifyLinkApplicationBacklink,
  linkApplicationCleanupDescription,
  linkApplicationCleanupSummary,
  linkApplicationEffectiveFields,
  linkApplicationOriginCommentErrorState,
  linkApplicationRejectDescription,
  linkApplicationReviewMode,
  linkApplicationStatusMeta,
} from "./link-application-review";

describe("buildLinkApplicationQuery", () => {
  it("passes pagination through and drops empty filters", () => {
    expect(buildLinkApplicationQuery({}, { page: 2, size: 20 })).toEqual({
      page: 2,
      size: 20,
      status: undefined,
      originType: undefined,
    });
  });

  it("propagates status and origin filters", () => {
    const query = buildLinkApplicationQuery({ status: "PENDING", originType: "COMMENT" }, { page: 1, size: 20 });
    expect(query.status).toBe("PENDING");
    expect(query.originType).toBe("COMMENT");
  });
});

describe("buildLinkApplicationCleanupParams", () => {
  it("reuses the current filters without pagination", () => {
    const params = buildLinkApplicationCleanupParams({
      status: "REJECTED",
      originType: "FORM",
    });
    expect(params).toEqual({
      status: "REJECTED",
      originType: "FORM",
    });
  });
});

describe("linkApplicationStatusMeta", () => {
  it("labels all lifecycle states", () => {
    expect(linkApplicationStatusMeta("PENDING")).toEqual({ label: "待审核", tagType: "warning" });
    expect(linkApplicationStatusMeta("APPROVING")).toEqual({ label: "审批中", tagType: "primary" });
    expect(linkApplicationStatusMeta("APPROVED")).toEqual({ label: "已通过", tagType: "success" });
    expect(linkApplicationStatusMeta("REJECTED")).toEqual({ label: "已拒绝", tagType: "danger" });
  });

  it("keeps unknown statuses readable", () => {
    expect(linkApplicationStatusMeta("SOMETHING")).toEqual({ label: "SOMETHING", tagType: "default" });
    expect(linkApplicationStatusMeta(undefined)).toEqual({ label: "未知", tagType: "default" });
  });
});

describe("linkApplicationReviewMode", () => {
  it("maps lifecycle states to review modes", () => {
    expect(linkApplicationReviewMode(application({ status: "PENDING" }))).toBe("editable");
    expect(linkApplicationReviewMode(application({ status: "APPROVING" }))).toBe("resume");
    expect(linkApplicationReviewMode(application({ status: "APPROVED" }))).toBe("readonly");
    expect(linkApplicationReviewMode(application({ status: "REJECTED" }))).toBe("readonly");
  });
});

describe("buildLinkApplicationApprovalRequest", () => {
  it("preserves explicit empty optional fields so approval can clear them", () => {
    expect(
      buildLinkApplicationApprovalRequest({
        url: " https://example.com ",
        displayName: " Example ",
        logo: "",
        description: "",
        groupName: "",
      }),
    ).toEqual({
      url: "https://example.com",
      displayName: "Example",
      logo: "",
      description: "",
      groupName: undefined,
    });
  });
});

describe("canVerifyLinkApplicationBacklink", () => {
  it("does not expose backlink verification while approval is reserved", () => {
    expect(
      canVerifyLinkApplicationBacklink(application({ status: "PENDING", backlink: "https://example.com/links" })),
    ).toBe(true);
    expect(
      canVerifyLinkApplicationBacklink(application({ status: "APPROVING", backlink: "https://example.com/links" })),
    ).toBe(false);
  });
});

describe("linkApplicationEffectiveFields", () => {
  it("prefers the frozen approval request", () => {
    const frozen: ApprovalRequest = {
      url: "https://frozen.example.com",
      displayName: "Frozen",
      groupName: "group-a",
    };
    const app = application({ status: "APPROVING", approval: { linkName: "link-a", request: frozen } });
    expect(linkApplicationEffectiveFields(app)).toEqual(frozen);
  });

  it("falls back to submission fields when no approval was reserved", () => {
    const app = application({ status: "PENDING" });
    expect(linkApplicationEffectiveFields(app)).toEqual({
      url: "https://example.com",
      displayName: "Example",
      logo: undefined,
      description: undefined,
    });
  });
});

describe("linkApplicationRejectDescription", () => {
  it("warns that form-origin rejections block resubmission while the record exists", () => {
    const description = linkApplicationRejectDescription(application({ originType: "FORM" }));
    expect(description).toContain("无法再次提交申请");
    expect(description).toContain("删除拒绝记录可解除限制");
  });

  it("treats historical applications without origin as form-origin", () => {
    const description = linkApplicationRejectDescription(application({}));
    expect(description).toContain("无法再次提交申请");
  });

  it("does not claim comment-origin URLs can never be submitted again", () => {
    const description = linkApplicationRejectDescription(application({ originType: "COMMENT" }));
    expect(description).toContain("仍可通过表单再次提交");
    expect(description).not.toContain("无法再次提交申请");
  });
});

describe("linkApplicationCleanupDescription", () => {
  it("uses the server total and always notes approving applications are kept", () => {
    const description = linkApplicationCleanupDescription({ total: 8 });
    expect(description).toContain("全部 8 条申请记录");
    expect(description).toContain("审批中的申请会保留");
  });

  it("warns about resubmission when the filter can include pending or rejected form applications", () => {
    expect(linkApplicationCleanupDescription({ total: 1 })).toContain("再次提交申请");
    expect(linkApplicationCleanupDescription({ total: 1, status: "PENDING" })).toContain("再次提交申请");
    expect(linkApplicationCleanupDescription({ total: 1, status: "REJECTED", originType: "FORM" })).toContain(
      "再次提交申请",
    );
    expect(linkApplicationCleanupDescription({ total: 1, status: "APPROVED" })).not.toContain("再次提交申请");
    expect(linkApplicationCleanupDescription({ total: 1, originType: "COMMENT" })).not.toContain("再次提交申请");
  });

  it("notes that deleting approved applications keeps the formal link", () => {
    expect(linkApplicationCleanupDescription({ total: 1 })).toContain("不会删除已创建的链接");
    expect(linkApplicationCleanupDescription({ total: 1, status: "APPROVED" })).toContain("不会删除已创建的链接");
    expect(linkApplicationCleanupDescription({ total: 1, status: "REJECTED" })).not.toContain("不会删除已创建的链接");
  });
});

describe("linkApplicationCleanupSummary", () => {
  it("summarizes matched and deleted counts", () => {
    expect(linkApplicationCleanupSummary({ matched: 5, deleted: 5, failed: 0, skipped: 0 })).toBe(
      "共匹配 5 条申请，已删除 5 条。",
    );
  });

  it("includes skipped approving and failed deletions when present", () => {
    expect(linkApplicationCleanupSummary({ matched: 6, deleted: 3, failed: 1, skipped: 2 })).toBe(
      "共匹配 6 条申请，已删除 3 条，跳过 2 条审批中的申请，1 条删除失败。",
    );
  });
});

describe("linkApplicationOriginCommentErrorState", () => {
  function axiosError(status: number) {
    return { isAxiosError: true, response: { status } };
  }

  it("treats 404 as unavailable source content", () => {
    expect(linkApplicationOriginCommentErrorState(axiosError(404))).toBe("unavailable");
  });

  it("distinguishes authorization failures from deleted content", () => {
    expect(linkApplicationOriginCommentErrorState(axiosError(403))).toBe("forbidden");
    expect(linkApplicationOriginCommentErrorState(axiosError(401))).toBe("forbidden");
  });

  it("falls back to a generic error for other failures", () => {
    expect(linkApplicationOriginCommentErrorState(axiosError(500))).toBe("error");
    expect(linkApplicationOriginCommentErrorState(new Error("network"))).toBe("error");
  });
});

function application(options: {
  status?: string;
  originType?: "FORM" | "COMMENT";
  approval?: { linkName: string; request: ApprovalRequest };
  backlink?: string;
}): LinkApplication {
  return {
    apiVersion: "core.halo.run/v1alpha1",
    kind: "LinkApplication",
    metadata: {
      name: "application-a",
    },
    spec: {
      displayName: "Example",
      status: options.status ?? "PENDING",
      url: "https://example.com",
      backlink: options.backlink,
      origin: options.originType ? { type: options.originType } : undefined,
      approval: options.approval,
    },
  } as LinkApplication;
}
