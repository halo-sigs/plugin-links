import { linksConsoleApiClient, linksCoreApiClient } from "@/api";
import type {
  ConsoleApiLinkHaloRunV1alpha1LinkApiListLinksRequest,
  Link,
  LinkGroup,
  LinkGroupV1alpha1ApiListLinkGroupRequest,
} from "@/api/generated";
import { paginate } from "@halo-dev/api-client";
import { useQuery } from "@tanstack/vue-query";

export const QK_GROUPS_WITH_LINKS = "plugin:links:groups-with-links";

export interface GroupWithLinks {
  group?: LinkGroup;
  links: Link[];
}

export function useLinksFetch() {
  return useQuery<GroupWithLinks[]>({
    queryKey: [QK_GROUPS_WITH_LINKS],
    queryFn: async () => {
      const groups = await paginate<LinkGroupV1alpha1ApiListLinkGroupRequest, LinkGroup>(
        (params) => linksCoreApiClient.group.listLinkGroup(params),
        {
          size: 1000,
          sort: ["spec.priority,asc"],
        },
      );

      const links = await paginate<ConsoleApiLinkHaloRunV1alpha1LinkApiListLinksRequest, Link>(
        (params) => linksConsoleApiClient.link.listLinks(params),
        {
          size: 1000,
          sort: ["spec.priority,asc"],
        },
      );

      if (!links.length) {
        return [{ links: [] }];
      }

      const groupNames = groups.map((group) => group.metadata.name);

      const ungrouped: GroupWithLinks = {
        group: undefined,
        links: links.filter((link) => !link.spec?.groupName || !groupNames.includes(link.spec.groupName)),
      };

      const grouped: GroupWithLinks[] = groups.map((group) => ({
        group,
        links: links.filter((link) => link.spec?.groupName === group.metadata.name),
      }));

      return [...grouped, ungrouped];
    },
    refetchInterval(data) {
      const hasDeletingGroup = data?.some((group) => {
        return !!group.group?.metadata.deletionTimestamp;
      });

      if (hasDeletingGroup) {
        return 1000;
      }

      const hasDeletingLink = data?.some((group) => {
        return group.links.some((link) => !!link.metadata.deletionTimestamp);
      });

      if (hasDeletingLink) {
        return 1000;
      }

      return false;
    },
  });
}
