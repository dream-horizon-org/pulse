import { useQuery } from "@tanstack/react-query";
import { API_ROUTES } from "../../constants";
import { getUserProjects } from "../../helpers/getUserProjects";
import { UserProjectsResponse } from "./useUserProjects.interface";
import { ApiResponse } from "../../helpers/makeRequest";

export const useUserProjects = (enabled: boolean = true) => {
  const route = API_ROUTES.GET_USER_PROJECTS;

  return useQuery<ApiResponse<UserProjectsResponse>>({
    queryKey: [route.key],
    queryFn: () => getUserProjects(),
    enabled,
    refetchOnWindowFocus: false,
    staleTime: 5 * 60 * 1000, // Consider data fresh for 5 minutes
  });
};
