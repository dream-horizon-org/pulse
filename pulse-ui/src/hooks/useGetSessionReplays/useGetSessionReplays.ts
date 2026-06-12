import { useQuery } from "@tanstack/react-query";
import {
  getQueryParamString,
  removeUndefinedValues,
} from "../../helpers/queryParams";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import {
  GetSessionReplaysQueryParams,
  SessionReplayResponse,
} from "./useGetSessionReplays.interface";
import { makeRequest } from "../../helpers/makeRequest";

export const useGetSessionReplays = ({
  interactionName,
  startTime,
  endTime,
  page = 0,
  pageSize = 20,
  eventTypes,
  device,
  enabled = true,
}: GetSessionReplaysQueryParams) => {
  const getSessionReplays = API_ROUTES.GET_SESSION_REPLAYS;
  const queryParams = {
    interactionName,
    startTime,
    endTime,
    page,
    pageSize,
    eventTypes: eventTypes?.join(","),
    device,
  };

  const searchParams = getQueryParamString(removeUndefinedValues(queryParams));

  return useQuery({
    queryKey: [
      getSessionReplays.key,
      interactionName,
      startTime,
      endTime,
      page,
      pageSize,
      JSON.stringify(eventTypes),
      device,
    ],
    queryFn: async () => {
      return makeRequest<SessionReplayResponse>({
        url: `${API_BASE_URL}${getSessionReplays.apiPath}${searchParams}`,
        init: {
          method: getSessionReplays.method,
        },
      });
    },
    refetchOnWindowFocus: false,
    enabled: enabled,
    staleTime: 0,
  });
};
