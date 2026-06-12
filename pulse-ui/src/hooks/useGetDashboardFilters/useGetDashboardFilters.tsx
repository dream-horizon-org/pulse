import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../../helpers/makeRequest";
import { DashboardFiltersResponse } from "./useGetDashboardFilters.interface";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";

export const useGetDashboardFilters = () => {
  const getDashboardFilters = API_ROUTES.GET_DASHBOARD_FILTERS;
  const enabled = useProjectQueryEnabled();

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
    enabled,
    refetchOnWindowFocus: false,
    refetchInterval: false,
  });
};
