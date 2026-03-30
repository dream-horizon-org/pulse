import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { useProjectContext } from "../../contexts";
import { makeRequest } from "../../helpers/makeRequest";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";
import { GetInteractionDiscoveriesResponse } from "./useGetInteractionDiscoveries.interface";

export function useGetInteractionDiscoveries(enabled = true) {
  const route = API_ROUTES.GET_INTERACTION_DISCOVERIES;
  const isProjectReady = useProjectQueryEnabled(enabled);
  const { projectId } = useProjectContext();

  return useQuery({
    queryKey: [route.key, projectId],
    queryFn: async () => {
      return makeRequest<GetInteractionDiscoveriesResponse>({
        url: `${API_BASE_URL}${route.apiPath}`,
        init: {
          method: route.method,
        },
      });
    },
    enabled: isProjectReady,
    retry: false,
  });
}
