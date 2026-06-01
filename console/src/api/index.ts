import { axiosInstance } from "@halo-dev/api-client";
import {
  ApiLinkHaloRunV1alpha1LinkGroupApi,
  ConsoleApiLinkHaloRunV1alpha1LinkApi,
  ConsoleApiLinkHaloRunV1alpha1LinkApplicationApi,
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
  application: new ConsoleApiLinkHaloRunV1alpha1LinkApplicationApi(undefined, "", axiosInstance),
};

export { linksConsoleApiClient, linksCoreApiClient, linksPublicApiClient };
