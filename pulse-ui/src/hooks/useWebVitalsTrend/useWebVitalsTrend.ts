import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { WebVitalsTrendResponse } from "../../screens/WebVitals/WebVitals.interface";
import { makeRequest } from "../../helpers/makeRequest";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";
import { removeUndefinedOrNullValues } from "../../helpers/queryParams";
import { UseWebVitalsTrendParams } from "./useWebVitalsTrend.interface";

export const useWebVitalsTrend = ({
  startTime,
  endTime,
  vitalName,
  bucketMinutes = 30,
  screenName,
}: UseWebVitalsTrendParams) => {
  const enabled = useProjectQueryEnabled();
  const route = API_ROUTES.GET_WEB_VITALS_TREND;

  const queryParams = removeUndefinedOrNullValues({
    startTime,
    endTime,
    vitalName,
    bucketMinutes,
    screenName,
  });

  const queryString = new URLSearchParams(queryParams).toString();
  const url = `${API_BASE_URL}${route.apiPath}?${queryString}`;

  return useQuery({
    queryKey: [route.key, startTime, endTime, vitalName, bucketMinutes, screenName],
    queryFn: async () => {
      return makeRequest<WebVitalsTrendResponse>({
        url,
        init: {
          method: route.method,
        },
      });
    },
    enabled,
    refetchOnWindowFocus: false,
  });
};
