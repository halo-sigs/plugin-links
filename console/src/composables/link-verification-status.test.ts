import type { Link } from "@/api/generated";
import { describe, expect, it } from "@rstest/core";
import {
  accessVerificationStatusMeta,
  backlinkVerificationStatusMeta,
  hasRunningLinkVerification,
  isLinkVerificationChecking,
  matchesLinkVerificationStatusFilter,
} from "./link-verification-status";

describe("accessVerificationStatusMeta", () => {
  it("classifies successful and failed access checks", () => {
    expect(
      accessVerificationStatusMeta(
        link({
          access: {
            state: "ACCESSIBLE",
            statusCode: 200,
          },
        }),
      ),
    ).toMatchObject({
      tone: "success",
      label: "访问正常",
    });

    expect(
      accessVerificationStatusMeta(
        link({
          access: {
            state: "INACCESSIBLE",
            error: "timeout",
          },
        }),
      ).tone,
    ).toBe("danger");
  });

  it("classifies unchecked links as muted", () => {
    expect(accessVerificationStatusMeta(link()).tone).toBe("muted");
  });
});

describe("backlinkVerificationStatusMeta", () => {
  it("classifies found and missing backlinks", () => {
    expect(
      backlinkVerificationStatusMeta(
        link({
          backlink: {
            state: "FOUND",
            matchedUrl: "https://halo.run/",
          },
        }),
      ),
    ).toMatchObject({
      tone: "success",
      label: "反链正常",
    });

    expect(
      backlinkVerificationStatusMeta(
        link({
          backlink: {
            state: "MISSING",
            scanUrl: "https://example.com/links",
          },
        }),
      ).tone,
    ).toBe("danger");
  });

  it("distinguishes configured-but-unchecked backlinks from unconfigured links", () => {
    expect(backlinkVerificationStatusMeta(link(undefined, "https://example.com/links")).label).toBe("反链未检测");
    expect(backlinkVerificationStatusMeta(link()).label).toBe("未配置反链");
  });
});

describe("verification checking helpers", () => {
  it("detects links currently being verified", () => {
    const checkingLink = link({
      access: {
        state: "CHECKING",
      },
    });

    expect(isLinkVerificationChecking(checkingLink)).toBe(true);
    expect(hasRunningLinkVerification([{ links: [link(), checkingLink] }])).toBe(true);
    expect(hasRunningLinkVerification([{ links: [link()] }])).toBe(false);
  });
});

describe("matchesLinkVerificationStatusFilter", () => {
  it("matches access errors and links without usable backlinks only for their selected filters", () => {
    const accessError = link({
      access: {
        state: "INACCESSIBLE",
      },
    });
    const missingBacklink = link({
      backlink: {
        state: "MISSING",
      },
    });
    const unconfiguredBacklink = link({
      backlink: {
        state: "NOT_CONFIGURED",
      },
    });
    const unconfiguredWithoutStatus = link();
    const configuredButUnchecked = link(undefined, "https://example.com/links");

    expect(matchesLinkVerificationStatusFilter(accessError, "all")).toBe(true);
    expect(matchesLinkVerificationStatusFilter(accessError, "access-error")).toBe(true);
    expect(matchesLinkVerificationStatusFilter(missingBacklink, "access-error")).toBe(false);
    expect(matchesLinkVerificationStatusFilter(missingBacklink, "backlink-missing")).toBe(true);
    expect(matchesLinkVerificationStatusFilter(unconfiguredBacklink, "backlink-missing")).toBe(true);
    expect(matchesLinkVerificationStatusFilter(unconfiguredWithoutStatus, "backlink-missing")).toBe(true);
    expect(matchesLinkVerificationStatusFilter(configuredButUnchecked, "backlink-missing")).toBe(false);
  });
});

function link(
  verification: NonNullable<NonNullable<Link["status"]>["verification"]> = {},
  backlinkScanUrl?: string,
): Link {
  return {
    metadata: {
      name: "link-a",
    },
    spec: {
      displayName: "Link A",
      url: "https://example.com",
      verification: backlinkScanUrl ? { backlinkScanUrl } : undefined,
    },
    status: {
      verification,
    },
  } as Link;
}
