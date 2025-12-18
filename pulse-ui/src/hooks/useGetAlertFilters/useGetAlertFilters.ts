import { useQuery } from "@tanstack/react-query";
import { GetAlertFilterResponse } from "./useGetAlertFilters.interface";
import { makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";

export const useGetAlertFilters = () => {
  const getAlertFilters = API_ROUTES.GET_ALERT_FILTERS;
  return useQuery({
    queryKey: [getAlertFilters.key],
    queryFn: async () => {
      return makeRequest<GetAlertFilterResponse>({
        url: `${API_BASE_URL}${getAlertFilters.apiPath}`,
        init: {
          method: getAlertFilters.method,
        },
      });
    },
    refetchOnWindowFocus: false,
    refetchInterval: false,
  });
};



