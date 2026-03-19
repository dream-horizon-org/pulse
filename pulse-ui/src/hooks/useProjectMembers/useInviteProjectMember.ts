import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { InviteProjectMemberParams, ProjectMember } from "../../types/members";

export const useInviteProjectMember = () => {
  const queryClient = useQueryClient();
  const route = API_ROUTES.INVITE_PROJECT_MEMBER;
  const getRoute = API_ROUTES.GET_PROJECT_MEMBERS;

  return useMutation<
    ApiResponse<ProjectMember>,
    unknown,
    InviteProjectMemberParams
  >({
    mutationFn: (params: InviteProjectMemberParams) =>
      makeRequest<ProjectMember>({
        url: `${API_BASE_URL}${route.apiPath.replace(":projectId", params.projectId)}`,
        init: {
          method: route.method,
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            email: params.email,
            role: params.role,
          }),
        },
      }),
    onSuccess: (data: ApiResponse<ProjectMember>, variables: InviteProjectMemberParams) => {
      if (data?.data && !data?.error) {
        queryClient.invalidateQueries({
          queryKey: [getRoute.key, variables.projectId],
        });
      }
    },
  });
};
