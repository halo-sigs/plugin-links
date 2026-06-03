import { axiosInstance } from "@halo-dev/api-client";
import type { AxiosResponse } from "axios";
import {
  ApiLinkHaloRunV1alpha1LinkGroupApi,
  ConsoleApiLinkHaloRunV1alpha1LinkApi,
  ConsoleApiLinkHaloRunV1alpha1LinkFeedApi,
  ConsoleApiLinkHaloRunV1alpha1LinkGroupApi,
  LinkGroupV1alpha1Api,
  LinkV1alpha1Api,
} from "./generated";

const linksCoreApiClient = {
  link: new LinkV1alpha1Api(undefined, "", axiosInstance),
  group: new LinkGroupV1alpha1Api(undefined, "", axiosInstance),
};

const linksPublicApiClient = {
  linkGroup: new ApiLinkHaloRunV1alpha1LinkGroupApi(undefined, "", axiosInstance),
};

const linksConsoleApiClient = {
  link: new ConsoleApiLinkHaloRunV1alpha1LinkApi(undefined, "", axiosInstance),
  feed: new ConsoleApiLinkHaloRunV1alpha1LinkFeedApi(undefined, "", axiosInstance),
  group: new ConsoleApiLinkHaloRunV1alpha1LinkGroupApi(undefined, "", axiosInstance),
};

export interface LinkCommentDTO {
  name: string;
  raw: string;
  content: string;
  ownerName?: string;
  ownerEmail?: string;
  creationTime?: string;
}

export interface LinkCommentAnalysisResult {
  url: string;
  displayName: string;
  logo?: string;
  description?: string;
  rssUrl?: string;
}

const linkAiApiClient = {
  listRecentComments(): Promise<AxiosResponse<LinkCommentDTO[]>> {
    return axiosInstance.get(
      "/apis/console.api.link.halo.run/v1alpha1/links/-/recent-comments"
    );
  },
  extractFromComment(content: string): Promise<AxiosResponse<LinkCommentAnalysisResult>> {
    return axiosInstance.post(
      "/apis/console.api.link.halo.run/v1alpha1/links/-/ai-extract",
      { content }
    );
  },
};

export { linksConsoleApiClient, linksCoreApiClient, linksPublicApiClient, linkAiApiClient };
