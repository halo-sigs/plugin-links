import { linksConsoleApiClient } from "@/api";
import type { ApproveRequest, LinkApplication, VerifyRequest } from "@/api/generated";
import type { LinkApplicationQuery } from "@/utils/link-application-review";
import { useMutation, useQuery, useQueryClient } from "@tanstack/vue-query";
import { computed, unref, type MaybeRef } from "vue";

export const QK_LINK_APPLICATIONS = "plugin:links:link-applications";
export const QK_LINK_APPLICATION_ORIGIN_COMMENT = "plugin:links:link-application-origin-comment";

export function useLinkApplications(params: MaybeRef<LinkApplicationQuery>) {
  const queryParams = computed(() => unref(params));
  return useQuery({
    queryKey: [QK_LINK_APPLICATIONS, queryParams],
    queryFn: async () => {
      const { data } = await linksConsoleApiClient.application.listLinkApplications({ ...queryParams.value });
      return data;
    },
    keepPreviousData: true,
    refetchInterval(data) {
      const hasDeletingData = data?.items?.some((item) => !!item.metadata?.deletionTimestamp);
      return hasDeletingData ? 1000 : false;
    },
  });
}

export function useLinkApplicationOriginComment(application: MaybeRef<LinkApplication>) {
  const target = computed(() => unref(application));
  const applicationName = computed(() => target.value.metadata?.name);
  const enabled = computed(() => target.value.spec.origin.type === "COMMENT" && !!applicationName.value);
  return useQuery({
    queryKey: [QK_LINK_APPLICATION_ORIGIN_COMMENT, applicationName],
    queryFn: async () => {
      if (!applicationName.value) {
        throw new Error("Application name is not available");
      }
      const { data } = await linksConsoleApiClient.application.getLinkApplicationOriginComment({
        name: applicationName.value,
      });
      return data;
    },
    enabled,
    retry: false,
  });
}

export function useApproveLinkApplication() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ name, request }: { name: string; request?: ApproveRequest }) => {
      const { data } = await linksConsoleApiClient.application.approveLinkApplication({
        name,
        approveRequest: request,
      });
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QK_LINK_APPLICATIONS] });
    },
  });
}

export function useRejectLinkApplication() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => linksConsoleApiClient.application.rejectLinkApplication({ name }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QK_LINK_APPLICATIONS] });
    },
  });
}

export function useDeleteLinkApplication() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => linksConsoleApiClient.application.deleteLinkApplication({ name }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QK_LINK_APPLICATIONS] });
    },
  });
}

export function useCleanupLinkApplications() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (params: Omit<LinkApplicationQuery, "page" | "size">) => {
      const { data } = await linksConsoleApiClient.application.cleanupLinkApplications({ ...params });
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QK_LINK_APPLICATIONS] });
    },
  });
}

export function useVerifyBacklink() {
  return useMutation({
    mutationFn: async ({ name, ...verifyRequest }: { name: string } & VerifyRequest) => {
      const { data } = await linksConsoleApiClient.application.verifyBacklink({
        name,
        verifyRequest,
      });
      return data;
    },
  });
}
