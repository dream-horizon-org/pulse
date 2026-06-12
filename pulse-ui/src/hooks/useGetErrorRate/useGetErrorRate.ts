import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { graphDataRequestBodyMaker } from "../../helpers/graphDataRequestBodyMaker";
import { makeRequest } from "../../helpers/makeRequest";
import {
  GraphDataParams,
  GetGraphDataResponse,
} from "../useGetApdexScore/useGetApdexScore.interface";

export const useGetErrorRate = ({
  requestBody,
  refetchInterval = undefined,
}: GraphDataParams) => {
  const getErrorRate = API_ROUTES.GET_ERROR_RATE;
  const filteredRequestBody = graphDataRequestBodyMaker(requestBody);

  return useQuery({
    queryKey: [getErrorRate.key, filteredRequestBody],
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
        url: `${API_BASE_URL}${getErrorRate.apiPath}`,
        init: {
          method: getErrorRate.method,
          body: JSON.stringify(filteredRequestBody),
        },
      });
    },
    refetchOnWindowFocus: true,
    refetchInterval,
  });
};
