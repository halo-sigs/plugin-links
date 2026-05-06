import { linksConsoleApiClient } from "@/api";
import { ApiPluginHaloRunV1alpha1LinkApiListLinksRequest, Link } from "@/api/generated";
import { paginate } from "@halo-dev/api-client";
import { useQuery } from "@tanstack/vue-query";

export const QK_LINKS = "plugin:links:links";

export function useLinkFetch() {
  return useQuery<Link[]>({
    queryKey: [QK_LINKS],
    queryFn: async () => {
      const data = await paginate<ApiPluginHaloRunV1alpha1LinkApiListLinksRequest, Link>(
        (params) => linksConsoleApiClient.link.listLinks(params),
        {
          size: 1000,
          sort: ["spec.priority,asc"],
        },
      );

      return data;
    },
    refetchInterval(data) {
      const hasDeletingData = data?.some((link) => !!link.metadata.deletionTimestamp);
      return hasDeletingData ? 1000 : false;
    },
  });
}
