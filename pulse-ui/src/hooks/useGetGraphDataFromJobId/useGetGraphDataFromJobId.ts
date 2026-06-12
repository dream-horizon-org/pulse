import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../../helpers/makeRequest";
import {
  GetGraphDataResponse,
  GraphDataParams,
} from "../useGetApdexScore/useGetApdexScore.interface";
import { graphDataRequestBodyMaker } from "../../helpers/graphDataRequestBodyMaker";

export const useGetGraphDataFromJobId = ({
  jobId = "",
  graphDataEndpoint,
  refetchInterval = undefined,
  requestBody,
}: GraphDataParams) => {
  const getGraphDataFromJobId = API_ROUTES.GET_GRAPH_DATA_FROM_JOB_ID;
  const filteredRequestBody = graphDataRequestBodyMaker(requestBody);
  const resolvedApiPath = getGraphDataFromJobId.apiPath
    .replace("{graphEndPoint}", graphDataEndpoint || "")
    .replace("{jobId}", jobId);

  return useQuery({
    queryKey: [getGraphDataFromJobId.key, jobId],
    queryFn: async () => {
      if (jobId === "") {
        return {
          data: null,
          error: {
            code: "",
            message: "Job ID is required",
            cause: "",
          },
        };
      }

      return makeRequest<GetGraphDataResponse>({
        url: `${API_BASE_URL}${resolvedApiPath}`,
        init: {
          method: getGraphDataFromJobId.method,
          body: JSON.stringify(filteredRequestBody),
        },
      });
    },
    refetchOnWindowFocus: true,
    refetchInterval,
    enabled: !!jobId,
  });
};
