import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import {
  GetDataQueryParams,
  DataQueryResponse,
} from "./useGetDataQuery.interface";
import { makeRequest } from "../../helpers/makeRequest";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";

dayjs.extend(utc);

// Helper to get projectId from sessionStorage (same source as makeRequestToServer)
const getProjectIdFromStorage = (): string | null => {
  try {
    const stored = sessionStorage.getItem('pulse_project_context');
    if (stored) {
      const data = JSON.parse(stored);
      return data.projectId || null;
    }
  } catch (error) {
    // Silently ignore parsing errors
  }
  return null;
};

export const useGetDataQuery = ({
  requestBody,
  enabled = true,
  refetchInterval = false,
}: GetDataQueryParams) => {
  const dataQuery = API_ROUTES.DATA_QUERY;
  
  // Read projectId from sessionStorage (single source of truth)
  const projectId = getProjectIdFromStorage();

  // Format times to ISO string
  // If the time is already in ISO format (contains 'T' or 'Z'), parse it directly
  // Otherwise, parse it as UTC (since getStartAndEndDateTimeString returns UTC times in "YYYY-MM-DD HH:mm:ss" format)
  const formatTime = (time: string): string => {
    if (time.includes("T") || time.includes("Z")) {
      // Already ISO format, just ensure it's valid
      return dayjs.utc(time).toISOString();
    }
    // Parse as UTC since the time string is already in UTC
    return dayjs.utc(time, "YYYY-MM-DD HH:mm:ss").toISOString();
  };

  const formattedStartTime = formatTime(requestBody.timeRange.start);
  const formattedEndTime = formatTime(requestBody.timeRange.end);

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
      projectId, // Include projectId to invalidate cache when project changes
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
    enabled: enabled && !!projectId, // Only enable if projectId exists
    staleTime: 10000,
    placeholderData: undefined,
  });
};
