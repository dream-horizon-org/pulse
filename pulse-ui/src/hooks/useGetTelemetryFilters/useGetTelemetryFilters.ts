import { useQuery } from "@tanstack/react-query";
import { API_ROUTES, API_BASE_URL } from "../../constants";
import { makeRequest } from "../../helpers/makeRequest";
import { TelemetryFiltersResponse } from "./useGetTelemetryFilters.interface";

export const useGetTelemetryFilters = () => {
  const route = API_ROUTES.GET_DASHBOARD_FILTERS;

  return useQuery({
    queryKey: [route.key],
    queryFn: async () => {
      return makeRequest<TelemetryFiltersResponse>({
        url: `${API_BASE_URL}${route.apiPath}`,
        init: { method: route.method },
      });
    },
    staleTime: 5 * 60 * 1000, // Cache for 5 minutes
  });
};

