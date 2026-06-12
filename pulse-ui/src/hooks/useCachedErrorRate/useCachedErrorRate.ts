import { useQuery } from "@tanstack/react-query";
import { API_ROUTES, API_BASE_URL } from "../../constants";
import {
  graphDataRequestBodyMaker,
  getMode,
} from "../../helpers/graphDataRequestBodyMaker";
import { makeRequest } from "../../helpers/makeRequest";
import { GraphDataParams } from "../useGetApdexScore/useGetApdexScore.interface";
import { ErrorRateResponse } from "./useCachedErrorRate.interface";

export const useCachedErrorRate = ({
  requestBody,
  refetchInterval = undefined,
  enabled = true,
}: GraphDataParams) => {
  const getCachedErrorRate = API_ROUTES.GET_CACHED_ERROR_RATE;
  const filteredRequestBody = graphDataRequestBodyMaker(requestBody);

  return useQuery({
    queryKey: [getCachedErrorRate.key, filteredRequestBody],
    queryFn: async () => {
      if (!filteredRequestBody.startTime || !filteredRequestBody.endTime) {
        return {
          data: null,
          error: {
            code: "",
            message: "Start time and end time are required",
            cause: "",
          },
        };
      }

      return makeRequest<ErrorRateResponse>({
        url: `${API_BASE_URL}${getCachedErrorRate.apiPath}`,
        init: {
          method: getCachedErrorRate.method,
          body: JSON.stringify(filteredRequestBody),
          headers: {
            "Data-Source": getMode(filteredRequestBody),
          },
        },
      });
    },
    refetchOnWindowFocus: true,
    refetchInterval,
    enabled: enabled,
  });
};
