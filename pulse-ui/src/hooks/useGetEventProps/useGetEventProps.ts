import { useQuery, UseQueryOptions } from "@tanstack/react-query";
import { API_BASE_URL } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { getQueryParamString } from "../../helpers/queryParams";
import {
  GetEventPropsErrorResponse,
  GetEventPropsRequestBody,
  GetEventPropsSuccessResponseBody,
} from "./useGetEventProps.interface";

type useGetEventPropsProps = Omit<
  UseQueryOptions<
    ApiResponse<GetEventPropsSuccessResponseBody>,
    GetEventPropsErrorResponse
  >,
  "queryFn" | "queryKey"
> &
  GetEventPropsRequestBody;

// Helper function to convert epoch timestamp to UTC
function convertEpochToUTC(epochSeconds: string): string {
  try {
    // Handle different timestamp formats
    let epochTime: number;

    if (epochSeconds.includes("E")) {
      // Scientific notation (e.g., "1.750764874132E9")
      epochTime = parseFloat(epochSeconds);
    } else {
      epochTime = parseFloat(epochSeconds);
    }

    const date = new Date(epochTime * 1000); // convert to milliseconds
    return date.toISOString(); // returns UTC time
  } catch (error) {
    console.error("Error converting epoch timestamp:", epochSeconds, error);
    // Fallback to current time in UTC
    return new Date().toISOString();
  }
}

export const useGetEventProps = ({
  eventName,
  eventTimestamp,
  userId,
  enabled = true,
  ...queryOptions
}: useGetEventPropsProps) => {
  return useQuery<
    ApiResponse<GetEventPropsSuccessResponseBody>,
    GetEventPropsErrorResponse
  >({
    queryKey: ["GET_EVENT_PROPS", eventName, eventTimestamp, userId],
    queryFn: async () => {
      // Convert epoch timestamp to UTC
      const utcTimestamp = convertEpochToUTC(eventTimestamp);

      const queryParams = getQueryParamString({
        eventName,
        eventTimestamp: utcTimestamp,
        userId,
      });
      return makeRequest<GetEventPropsSuccessResponseBody>({
        url: `${API_BASE_URL}/v2/events/eventname${queryParams}`,
        init: {
          method: "GET",
        },
      });
    },
    enabled: false,
    refetchOnWindowFocus: false,
    staleTime: 5 * 60 * 1000, // 5 minutes
    ...queryOptions,
  });
};
