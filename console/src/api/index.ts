import { axiosInstance } from "@halo-dev/api-client";
import { ApiPluginHaloRunV1alpha1LinkApi, LinkGroupV1alpha1Api, LinkV1alpha1Api } from "./generated";

const linksCoreApiClient = {
  link: new LinkV1alpha1Api(undefined, "", axiosInstance),
  group: new LinkGroupV1alpha1Api(undefined, "", axiosInstance),
};

const linksConsoleApiClient = {
  link: new ApiPluginHaloRunV1alpha1LinkApi(undefined, "", axiosInstance),
};

export { linksConsoleApiClient, linksCoreApiClient };
