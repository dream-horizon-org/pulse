import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { WebVitalsByScreenResponse } from "../../screens/WebVitals/WebVitals.interface";
import { makeRequest } from "../../helpers/makeRequest";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";
import { UseWebVitalsByScreenParams } from "./useWebVitalsByScreen.interface";

export const useWebVitalsByScreen = ({
  startTime,
  endTime,
  vitalName,
}: UseWebVitalsByScreenParams) => {
  const enabled = useProjectQueryEnabled();
  const route = API_ROUTES.GET_WEB_VITALS_BY_SCREEN;

  const queryParams = {
    startTime,
    endTime,
    vitalName,
  };

  const queryString = new URLSearchParams(queryParams).toString();
  const url = `${API_BASE_URL}${route.apiPath}?${queryString}`;

  return useQuery({
    queryKey: [route.key, startTime, endTime, vitalName],
    queryFn: async () => {
      return makeRequest<WebVitalsByScreenResponse>({
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
