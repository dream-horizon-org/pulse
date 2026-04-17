import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { GetSuggestedInteractionsResponse } from "./useGetSuggestedInteractions.interface";
import { makeRequest } from "../../helpers/makeRequest";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";

export const useGetSuggestedInteractions = (enabled = true) => {
  const route = API_ROUTES.GET_SUGGESTED_INTERACTIONS;
  const isProjectReady = useProjectQueryEnabled(enabled);

  return useQuery({
    queryKey: [route.key],
    queryFn: async () => {
      return makeRequest<GetSuggestedInteractionsResponse>({
        url: `${API_BASE_URL}${route.apiPath}`,
        init: {
          method: route.method,
        },
      });
    },
    refetchOnWindowFocus: false,
    enabled: isProjectReady,
    staleTime: 5 * 60 * 1000,
  });
};
