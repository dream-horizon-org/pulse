import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import {
  FunnelResponse,
  FunnelHealthResponse,
  FunnelSessionsResponse,
  GetFunnelDataParams,
  GetFunnelHealthParams,
  GetFunnelSessionsParams,
} from "./useGetFunnelData.interface";
import { makeRequest } from "../../helpers/makeRequest";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";

dayjs.extend(utc);

const formatTime = (time: string): string => {
  if (time.includes("T") || time.includes("Z")) {
    return dayjs.utc(time).toISOString();
  }
  return dayjs.utc(time, "YYYY-MM-DD HH:mm:ss").toISOString();
};

export const useGetFunnelData = ({
  requestBody,
  enabled = true,
}: GetFunnelDataParams) => {
  const funnelApi = API_ROUTES.FUNNEL_ANALYZE;

  const modifiedRequestBody = {
    ...requestBody,
    timeRange: {
      start: formatTime(requestBody.timeRange.start),
      end: formatTime(requestBody.timeRange.end),
    },
  };

  return useQuery({
    queryKey: [
      funnelApi.key,
      JSON.stringify(requestBody.steps),
      requestBody.timeRange.start,
      requestBody.timeRange.end,
      JSON.stringify(requestBody.filters),
      requestBody.groupBy,
      requestBody.mode,
    ],
    queryFn: async () => {
      return makeRequest<FunnelResponse>({
        url: `${API_BASE_URL}${funnelApi.apiPath}`,
        init: {
          method: funnelApi.method,
          body: JSON.stringify(modifiedRequestBody),
        },
      });
    },
    refetchOnWindowFocus: false,
    enabled:
      enabled &&
      requestBody.steps.length >= 2 &&
      requestBody.steps.every((s) => s.eventName.trim() !== ""),
    staleTime: 10000,
  });
};

export const useGetFunnelHealth = ({
  requestBody,
  enabled = true,
}: GetFunnelHealthParams) => {
  const api = API_ROUTES.FUNNEL_HEALTH;

  const modifiedRequestBody = {
    ...requestBody,
    timeRange: {
      start: formatTime(requestBody.timeRange.start),
      end: formatTime(requestBody.timeRange.end),
    },
  };

  return useQuery({
    queryKey: [
      api.key,
      JSON.stringify(requestBody.steps),
      requestBody.timeRange.start,
      requestBody.timeRange.end,
      requestBody.mode,
    ],
    queryFn: async () => {
      return makeRequest<FunnelHealthResponse>({
        url: `${API_BASE_URL}${api.apiPath}`,
        init: {
          method: api.method,
          body: JSON.stringify(modifiedRequestBody),
        },
      });
    },
    refetchOnWindowFocus: false,
    enabled:
      enabled &&
      requestBody.steps.length >= 2 &&
      requestBody.steps.every((s) => s.eventName.trim() !== ""),
    staleTime: 10000,
  });
};

export const useGetFunnelSessions = ({
  requestBody,
  enabled = true,
}: GetFunnelSessionsParams) => {
  const api = API_ROUTES.FUNNEL_SESSIONS;

  const modifiedRequestBody = {
    ...requestBody,
    timeRange: {
      start: formatTime(requestBody.timeRange.start),
      end: formatTime(requestBody.timeRange.end),
    },
  };

  return useQuery({
    queryKey: [
      api.key,
      JSON.stringify(requestBody.steps),
      requestBody.timeRange.start,
      requestBody.timeRange.end,
      requestBody.stepLevel,
      requestBody.issueType,
      requestBody.mode,
    ],
    queryFn: async () => {
      return makeRequest<FunnelSessionsResponse>({
        url: `${API_BASE_URL}${api.apiPath}`,
        init: {
          method: api.method,
          body: JSON.stringify(modifiedRequestBody),
        },
      });
    },
    refetchOnWindowFocus: false,
    enabled: enabled && requestBody.stepLevel >= 1,
    staleTime: 10000,
  });
};
