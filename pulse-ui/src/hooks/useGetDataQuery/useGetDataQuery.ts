import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import {
  GetDataQueryParams,
  DataQueryResponse,
} from "./useGetDataQuery.interface";
import { makeRequest } from "../../helpers/makeRequest";
import dayjs from "dayjs";

export const useGetDataQuery = ({
  requestBody,
  enabled = true,
  refetchInterval = false,
}: GetDataQueryParams) => {
  const dataQuery = API_ROUTES.DATA_QUERY;


  // make sure we send the start and end time in the correct format
  const formattedStartTime = dayjs(requestBody.timeRange.start).toISOString();
  const formattedEndTime = dayjs(requestBody.timeRange.end).toISOString();

  const modifiedRequestBody = {
    ...requestBody,
    timeRange: {
      start: formattedStartTime,
      end: formattedEndTime,
    },
  };

  return useQuery({
    queryKey: [
      dataQuery.key,
      requestBody.dataType,
      requestBody.timeRange.start,
      requestBody.timeRange.end,
      JSON.stringify(requestBody.select),
      JSON.stringify(requestBody.groupBy),
      JSON.stringify(requestBody.filters),
    ],
    queryFn: async () => {
      return makeRequest<DataQueryResponse>({
        url: `${API_BASE_URL}${dataQuery.apiPath}`,
        init: {
          method: dataQuery.method,
          body: JSON.stringify(modifiedRequestBody),
        },
      });
    },
    refetchOnWindowFocus: false,
    refetchInterval,
    enabled: enabled,
    staleTime: 0,
    placeholderData: undefined,
  });
};
