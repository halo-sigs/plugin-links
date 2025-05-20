import { linksConsoleApiClient, linksCoreApiClient } from "@/api";
import { LinkGroup } from "@/api/generated";
import { useQuery } from "@tanstack/vue-query";
import { ref, type Ref } from "vue";

export function useLinkFetch(page: Ref<number>, size: Ref<number>, keyword?: Ref<string>, group?: Ref<string>) {
  const total = ref(0);

  const {
    data: links,
    isLoading,
    refetch,
  } = useQuery({
    queryKey: ["links", page, size, group, keyword],
    queryFn: async () => {
      const { data } = await linksConsoleApiClient.link.listLinks({
        page: page.value,
        size: size.value,
        keyword: keyword?.value,
        groupName: group?.value,
        sort: ["spec.priority,asc"],
      });

      total.value = data.total;

      return data.items;
    },
    refetchOnWindowFocus: false,
    refetchInterval(data) {
      const deletingLinks = data?.filter((link) => !!link.metadata.deletionTimestamp);
      return deletingLinks?.length ? 1000 : false;
    },
  });

  return {
    links,
    isLoading,
    refetch,
    total,
  };
}

export function useLinkGroupFetch() {
  const {
    data: groups,
    isLoading,
    refetch,
  } = useQuery<LinkGroup[]>({
    queryKey: ["link-groups"],
    queryFn: async () => {
      const { data } = await linksCoreApiClient.group.listLinkGroup();

      return data.items
        .map((group) => {
          if (group.spec) {
            group.spec.priority = group.spec.priority || 0;
          }
          return group;
        })
        .sort((a, b) => {
          return (a.spec?.priority || 0) - (b.spec?.priority || 0);
        });
    },
    refetchOnWindowFocus: false,
    refetchInterval(data) {
      const hasDeletingData = data?.some((group) => {
        return !!group.metadata.deletionTimestamp;
      });
      return hasDeletingData ? 1000 : false;
    },
  });

  return {
    groups,
    isLoading,
    refetch,
  };
}
