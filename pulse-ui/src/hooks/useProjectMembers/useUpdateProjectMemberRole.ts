import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { UpdateProjectMemberRoleParams, ProjectMember } from "../../types/members";

export const useUpdateProjectMemberRole = () => {
  const queryClient = useQueryClient();
  const route = API_ROUTES.UPDATE_PROJECT_MEMBER_ROLE;
  const getRoute = API_ROUTES.GET_PROJECT_MEMBERS;

  return useMutation<
    ApiResponse<ProjectMember>,
    unknown,
    UpdateProjectMemberRoleParams
  >({
    mutationFn: (params: UpdateProjectMemberRoleParams) =>
      makeRequest<ProjectMember>({
        url: `${API_BASE_URL}${route.apiPath
          .replace(":projectId", params.projectId)
          .replace(":userId", params.userId)}`,
        init: {
          method: route.method,
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            newRole: params.newRole,
          }),
        },
      }),
    onSuccess: (data: ApiResponse<ProjectMember>, variables: UpdateProjectMemberRoleParams) => {
      if (data?.data && !data?.error) {
        queryClient.invalidateQueries({
          queryKey: [getRoute.key, variables.projectId],
        });
      }
    },
  });
};
