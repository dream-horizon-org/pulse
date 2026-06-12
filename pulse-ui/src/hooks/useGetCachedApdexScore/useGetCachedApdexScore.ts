import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../../helpers/makeRequest";
import {
  getMode,
  graphDataRequestBodyMaker,
} from "../../helpers/graphDataRequestBodyMaker";
import { GraphDataParams } from "../useGetApdexScore/useGetApdexScore.interface";
import { ApdexResponse } from "./useCachedGetApdexScore.interface";

export const useCachedGetApdexScore = ({
  requestBody,
  refetchInterval = undefined,
  enabled = true,
}: GraphDataParams) => {
  const getApdexScore = API_ROUTES.GET_CACHED_APDEX_SCORE;
  const filteredRequestBody = graphDataRequestBodyMaker(requestBody);

  return useQuery({
    queryKey: [getApdexScore.key, filteredRequestBody],
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

      return makeRequest<ApdexResponse>({
        url: `${API_BASE_URL}${getApdexScore.apiPath}`,
        init: {
          method: getApdexScore.method,
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
