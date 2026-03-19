import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../../helpers/makeRequest";
import { DashboardFiltersResponse } from "./useGetDashboardFilters.interface";

export const useGetDashboardFilters = () => {
  const getDashboardFilters = API_ROUTES.GET_DASHBOARD_FILTERS;

  return useQuery({
    queryKey: [getDashboardFilters.key],
    queryFn: async () => {
      return makeRequest<DashboardFiltersResponse>({
        url: `${API_BASE_URL}${getDashboardFilters.apiPath}`,
        init: {
          method: getDashboardFilters.method,
        },
      });
    },
    refetchOnWindowFocus: false,
    refetchInterval: false,
  });
};