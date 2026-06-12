import { useQuery } from "@tanstack/react-query";
import { makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { GetAlertSeveritiesResponse } from "./useGetAlertSeverities.interface";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";

export const useGetAlertSeverities = () => {
  const getAlertSeverities = API_ROUTES.GET_ALERT_SEVERITIES;
  const enabled = useProjectQueryEnabled();

  return useQuery({
    queryKey: [getAlertSeverities.key],
    queryFn: async () => {
      return makeRequest<GetAlertSeveritiesResponse>({
        url: `${API_BASE_URL}${getAlertSeverities.apiPath}`,
        init: {
          method: getAlertSeverities.method,
        },
      });
    },
    enabled,
    refetchOnWindowFocus: false,
    refetchInterval: false,
  });
};
