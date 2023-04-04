import type { LinkList } from "@/types";
import apiClient from "@/utils/api-client";
import { useQuery } from "@tanstack/vue-query";
import { ref, type Ref } from "vue";

export function useLinkFetch(
  page: Ref<number>,
  size: Ref<number>,
  keyword: Ref<string>,
  group: Ref<string>
) {
  const total = ref(0);

  const {
    data: links,
    isLoading,
    refetch,
  } = useQuery({
    queryKey: ["links", page, size, group, keyword],
    queryFn: async () => {
      const url = group.value
        ? `/apis/api.plugin.halo.run/v1alpha1/plugins/PluginLinks/groups/${group.value}/links`
        : "/apis/core.halo.run/v1alpha1/links";

      const { data } = await apiClient.get<LinkList>(url, {
        params: {
          page: page.value,
          size: size.value,
          keyword: keyword.value,
        },
      });

      total.value = data.total;

      return data.items;
    },
    refetchOnWindowFocus: false,
    refetchInterval(data) {
      const deletingLinks = data?.filter(
        (link) => !!link.metadata.deletionTimestamp
      );
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
