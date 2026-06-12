import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import {
  GraphDataParams,
  GetGraphDataResponse,
} from "./useGetApdexScore.interface";
import { makeRequest } from "../../helpers/makeRequest";
import { graphDataRequestBodyMaker } from "../../helpers/graphDataRequestBodyMaker";

export const useGetApdexScore = ({
  requestBody,
  refetchInterval = undefined,
  enabled = true,
}: GraphDataParams) => {
  const getApdexScore = API_ROUTES.GET_APDEX_SCORE;
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

      return makeRequest<GetGraphDataResponse>({
        url: `${API_BASE_URL}${getApdexScore.apiPath}`,
        init: {
          method: getApdexScore.method,
          body: JSON.stringify(filteredRequestBody),
        },
      });
    },
    refetchOnWindowFocus: true,
    refetchInterval,
    enabled: enabled,
  });
};
