import { linksCoreApiClient } from "@/api";
import { LinkGroup, type LinkGroupV1alpha1ApiListLinkGroupRequest } from "@/api/generated";
import { paginate } from "@halo-dev/api-client";
import { useQuery } from "@tanstack/vue-query";

export const QK_LINK_GROUPS = "plugin:links:link-groups";

export function useLinkGroupFetch() {
  return useQuery<LinkGroup[]>({
    queryKey: [QK_LINK_GROUPS],
    queryFn: async () => {
      const data = await paginate<LinkGroupV1alpha1ApiListLinkGroupRequest, LinkGroup>(
        (params) => linksCoreApiClient.group.listLinkGroup(params),
        {
          size: 1000,
          sort: ["spec.priority,asc"],
        },
      );

      return data;
    },
    refetchInterval(data) {
      const hasDeletingData = data?.some((group) => {
        return !!group.metadata.deletionTimestamp;
      });
      return hasDeletingData ? 1000 : false;
    },
  });
}
