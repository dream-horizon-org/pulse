import { useQuery } from "@tanstack/react-query";
import { API_ROUTES } from "../../constants";
import { getProjectApiKey } from "../../helpers/getProjectApiKey";
import { ProjectApiKeyResult } from "./useProjectApiKeys.interface";

export const useProjectApiKey = (projectId: string) => {
  const route = API_ROUTES.GET_PROJECT_API_KEYS;

  return useQuery<ProjectApiKeyResult>({
    queryKey: [route.key, projectId],
    queryFn: () => getProjectApiKey(projectId),
    enabled: !!projectId,
    refetchOnWindowFocus: false,
  });
};
