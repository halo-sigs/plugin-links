import { linksConsoleApiClient, linksCoreApiClient } from "@/api";
import type {
  ConsoleApiLinkHaloRunV1alpha1LinkApiListLinksRequest,
  Link,
  LinkGroup,
  LinkGroupV1alpha1ApiListLinkGroupRequest,
} from "@/api/generated";
import { paginate } from "@halo-dev/api-client";
import { useQuery } from "@tanstack/vue-query";
import { hasRunningLinkVerification } from "./link-verification-status";

export const QK_GROUPS_WITH_LINKS = "plugin:links:groups-with-links";
export const QK_RSS_GROUPS_WITH_LINKS = "plugin:links:rss-groups-with-links";

export interface GroupWithLinks {
  group?: LinkGroup;
  links: Link[];
}

function groupLinks(groups: LinkGroup[], links: Link[]) {
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
}

function hasRssFeedUrls(link: Link) {
  return Boolean(link.spec?.rss?.feedUrls?.some((feedUrl) => !!feedUrl?.trim()));
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

      return groupLinks(groups, links);
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

      if (hasRunningLinkVerification(data)) {
        return 1000;
      }

      return false;
    },
  });
}

export function useRssLinksFetch() {
  return useQuery<GroupWithLinks[]>({
    queryKey: [QK_RSS_GROUPS_WITH_LINKS],
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
          fieldSelector: ["spec.rss.enabled=true"],
        },
      );

      return groupLinks(
        groups,
        links.filter((link) => link.spec?.rss?.enabled && hasRssFeedUrls(link)),
      );
    },
  });
}
