import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { GetInteractionListFiltersResponse } from "./useGetInteractionListFilters.interface";
import { makeRequest } from "../../helpers/makeRequest";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";

export const useGetInteractionListFilters = () => {
  const getInteractionListFilters = API_ROUTES.GET_INTERACTIONLIST_FILTERS;
  const enabled = useProjectQueryEnabled();

  return useQuery<GetInteractionListFiltersResponse>({
    queryKey: [getInteractionListFilters?.key],
    queryFn: async () => {
      const response = await makeRequest<GetInteractionListFiltersResponse>({
        url: `${API_BASE_URL}${getInteractionListFilters.apiPath}`,
        init: {
          method: getInteractionListFilters.method,
        },
      });
      // Transform the API response: map createdBy to users
      const transformedData: GetInteractionListFiltersResponse = {
        createdBy: response.data?.createdBy || [],
        statuses: response.data?.statuses || [],
      };
      return transformedData;
    },
    enabled,
    refetchOnWindowFocus: false,
  });
};
