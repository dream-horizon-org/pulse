import { useQuery } from "@tanstack/react-query";
import { makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { GetAlertScopesResponse } from "./useGetAlertScopes.interface";

export const useGetAlertScopes = () => {
  const getAlertScopes = API_ROUTES.GET_ALERT_SCOPES;
  return useQuery({
    queryKey: [getAlertScopes.key],
    queryFn: async () => {
      return makeRequest<GetAlertScopesResponse>({
        url: `${API_BASE_URL}${getAlertScopes.apiPath}`,
        init: {
          method: getAlertScopes.method,
        },
      });
    },
    refetchOnWindowFocus: false,
    refetchInterval: false,
  });
};




