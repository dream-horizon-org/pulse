import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { GetSuggestedQueriesResponse } from "./useGetSuggestedQueries.interface";
import { makeRequest } from "../../helpers/makeRequest";

export const useGetSuggestedQueries = () => {
  const getSuggestedQueries = API_ROUTES.GET_SUGGESTED_QUERIES;

  return useQuery({
    queryKey: [getSuggestedQueries.key],
    queryFn: async () => {
      return makeRequest<GetSuggestedQueriesResponse>({
        url: `${API_BASE_URL}${getSuggestedQueries.apiPath}`,
        init: {
          method: getSuggestedQueries.method,
        },
      });
    },
    refetchOnWindowFocus: false,
  });
};
