import { linksConsoleApiClient } from "@/api";
import type {
  ApproveRequest,
  ConsoleApiLinkHaloRunV1alpha1LinkApplicationApiApproveLinkApplicationRequest,
} from "@/api/generated";
import { useMutation, useQuery, useQueryClient } from "@tanstack/vue-query";

export const QK_LINK_APPLICATIONS = "plugin:links:link-applications";

export function useLinkApplications(status?: string) {
  return useQuery({
    queryKey: [QK_LINK_APPLICATIONS, status],
    queryFn: async () => {
      const { data } = await linksConsoleApiClient.application.listLinkApplications({
        status,
      });
      return data.items || [];
    },
  });
}

export function useApproveLinkApplication() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      name,
      request,
    }: {
      name: string;
      request: ApproveRequest;
    }) => {
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
    mutationFn: (name: string) =>
      linksConsoleApiClient.application.rejectLinkApplication({ name }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QK_LINK_APPLICATIONS] });
    },
  });
}

export function useDeleteLinkApplication() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (name: string) =>
      linksConsoleApiClient.application.deleteLinkApplication({ name }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QK_LINK_APPLICATIONS] });
    },
  });
}

export function useVerifyBacklink() {
  return useMutation({
    mutationFn: async (name: string) => {
      const { data } = await linksConsoleApiClient.application.verifyBacklink({ name });
      return data;
    },
  });
}
