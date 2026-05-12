import { axiosInstance } from "@halo-dev/api-client";
import {
  ConsoleApiLinkHaloRunV1alpha1LinkApi,
  ConsoleApiLinkHaloRunV1alpha1LinkGroupApi,
  LinkGroupV1alpha1Api,
  LinkV1alpha1Api,
} from "./generated";

const linksCoreApiClient = {
  link: new LinkV1alpha1Api(undefined, "", axiosInstance),
  group: new LinkGroupV1alpha1Api(undefined, "", axiosInstance),
};

const linksConsoleApiClient = {
  link: new ConsoleApiLinkHaloRunV1alpha1LinkApi(undefined, "", axiosInstance),
  group: new ConsoleApiLinkHaloRunV1alpha1LinkGroupApi(undefined, "", axiosInstance),
};

export { linksConsoleApiClient, linksCoreApiClient };
