import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { getQueryParamString } from "../../helpers/queryParams";
import {
  GetScreeNameToEvenQueryMappingParams,
  GetScreeNameToEvenQueryMappingResponse,
} from "./useGetScreenNameToEventQueryMapping.interface";
import { makeRequest } from "../../helpers/makeRequest";

export const useGetScreenNameToEventQueryMapping = ({
  queryParams = null,
}: GetScreeNameToEvenQueryMappingParams) => {
  const getScreenNameToEventQueryMapping =
    API_ROUTES.GET_SCREEN_NAME_EVENTS_MAPPING;

  const searchParams = getQueryParamString(queryParams);

  return useQuery({
    queryKey: [
      getScreenNameToEventQueryMapping.key,
      queryParams?.search_string,
      queryParams?.limit,
    ],
    queryFn: async () => {
      const { search_string } = queryParams || {};

      if (!search_string) {
        return {
          data: { eventList: [], recordCount: 0 },
          error: null,
        };
      }

      return makeRequest<GetScreeNameToEvenQueryMappingResponse>({
        url: `${API_BASE_URL}${
          getScreenNameToEventQueryMapping.apiPath
        }${searchParams}`,
        init: {
          method: getScreenNameToEventQueryMapping.method,
        },
      });
    },
    placeholderData: keepPreviousData,
    enabled: !!queryParams?.search_string?.trim(),
  });
};
