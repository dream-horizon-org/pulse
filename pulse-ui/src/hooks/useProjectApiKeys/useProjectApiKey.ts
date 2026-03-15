import { useQuery } from "@tanstack/react-query";
import { API_ROUTES } from "../../constants";
import { getProjectApiKey } from "../../helpers/getProjectApiKey";
import { ProjectApiKeyResult } from "./useProjectApiKeys.interface";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";

interface UseProjectApiKeyOptions {
  enabled?: boolean;
}

export const useProjectApiKey = (
  projectId: string,
  options?: UseProjectApiKeyOptions,
) => {
  const route = API_ROUTES.GET_PROJECT_API_KEYS;
  const isProjectReady = useProjectQueryEnabled(
    options?.enabled !== undefined ? options.enabled : !!projectId,
  );

  return useQuery<ProjectApiKeyResult>({
    queryKey: [route.key, projectId],
    queryFn: () => getProjectApiKey(projectId),
    enabled: isProjectReady,
    refetchOnWindowFocus: false,
  });
};
