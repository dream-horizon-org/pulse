import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import {
  GraphDataParams,
  GetGraphDataResponse,
} from "../useGetApdexScore/useGetApdexScore.interface";
import { makeRequest } from "../../helpers/makeRequest";
import { graphDataRequestBodyMaker } from "../../helpers/graphDataRequestBodyMaker";

export const useGetInteractionTime = ({
  requestBody,
  refetchInterval = 0,
}: GraphDataParams) => {
  const getInteractionTime = API_ROUTES.GET_INTERACTION_TIME_DIFF_PERCENTILE;
  const filteredRequestBody = graphDataRequestBodyMaker(requestBody);

  return useQuery({
    queryKey: [getInteractionTime.key, filteredRequestBody],
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
        url: `${API_BASE_URL}${getInteractionTime.apiPath}`,
        init: {
          method: getInteractionTime.method,
          body: JSON.stringify(filteredRequestBody),
        },
      });
    },
    refetchOnWindowFocus: true,
    refetchInterval,
  });
};
