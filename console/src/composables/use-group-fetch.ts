import { linksPublicApiClient } from "@/api";
import type { LinkGroupVo } from "@/api/generated";
import { useQuery } from "@tanstack/vue-query";

export const QK_LINK_GROUPS = "plugin:links:link-groups";

export function useLinkGroupFetch() {
  return useQuery<LinkGroupVo[]>({
    queryKey: [QK_LINK_GROUPS],
    queryFn: async () => {
      const { data } = await linksPublicApiClient.linkGroup.queryLinkGroups();
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
