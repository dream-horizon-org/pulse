import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import {
  GetSessionDataParams,
  SessionDataResponse,
  RawDataResponse,
} from "./useGetSessionData.interface";
import { makeRequest } from "../../helpers/makeRequest";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";

dayjs.extend(utc);

// Helper to format time
const formatTime = (time: string): string => {
  if (time.includes("T") || time.includes("Z")) {
    return dayjs.utc(time).toISOString();
  }
  return dayjs.utc(time, "YYYY-MM-DD HH:mm:ss").toISOString();
};

/**
 * Fetches traces for a session
 */
const fetchSessionTraces = async (sessionId: string, timeRange: { start: string; end: string }) => {
  const formattedStartTime = formatTime(timeRange.start);
  const formattedEndTime = formatTime(timeRange.end);

  const requestBody = {
    dataType: "TRACES",
    timeRange: {
      start: formattedStartTime,
      end: formattedEndTime,
    },
    select: [
      { function: "COL", param: { field: "TraceId" }, alias: "traceId" },
      { function: "COL", param: { field: "SpanId" }, alias: "spanId" },
      { function: "COL", param: { field: "ParentSpanId" }, alias: "parentSpanId" },
      { function: "COL", param: { field: "SpanName" }, alias: "spanName" },
      { function: "COL", param: { field: "SpanKind" }, alias: "spanKind" },
      { function: "COL", param: { field: "ServiceName" }, alias: "serviceName" },
      { function: "COL", param: { field: "Timestamp" }, alias: "timestamp" },
      { function: "COL", param: { field: "Duration" }, alias: "duration" },
      { function: "COL", param: { field: "StatusCode" }, alias: "statusCode" },
      { function: "COL", param: { field: "PulseType" }, alias: "pulseType" },
    ],
    filters: [
      {
        field: "SessionId",
        operator: "EQ",
        value: [sessionId],
      },
    ],
    orderBy: [{ field: "Timestamp", direction: "ASC" }],
    limit: 10000,
  };

  const dataQuery = API_ROUTES.DATA_QUERY;
  
  return makeRequest<RawDataResponse>({
    url: `${API_BASE_URL}${dataQuery.apiPath}`,
    init: {
      method: dataQuery.method,
      body: JSON.stringify(requestBody),
    },
  });
};

/**
 * Fetches logs for a session
 */
const fetchSessionLogs = async (sessionId: string, timeRange: { start: string; end: string }) => {
  const formattedStartTime = formatTime(timeRange.start);
  const formattedEndTime = formatTime(timeRange.end);

  const requestBody = {
    dataType: "LOGS",
    timeRange: {
      start: formattedStartTime,
      end: formattedEndTime,
    },
    select: [
      { function: "COL", param: { field: "TraceId" }, alias: "traceId" },
      { function: "COL", param: { field: "SpanId" }, alias: "spanId" },
      { function: "COL", param: { field: "Timestamp" }, alias: "timestamp" },
      { function: "COL", param: { field: "SeverityText" }, alias: "severityText" },
      { function: "COL", param: { field: "Body" }, alias: "body" },
      { function: "COL", param: { field: "PulseType" }, alias: "pulseType" },
    ],
    filters: [
      {
        field: "SessionId",
        operator: "EQ",
        value: [sessionId],
      },
    ],
    orderBy: [{ field: "Timestamp", direction: "ASC" }],
    limit: 10000,
  };

  const dataQuery = API_ROUTES.DATA_QUERY;

  return makeRequest<RawDataResponse>({
    url: `${API_BASE_URL}${dataQuery.apiPath}`,
    init: {
      method: dataQuery.method,
      body: JSON.stringify(requestBody),
    },
  });
};

/**
 * Fetches exceptions (crashes, ANRs, non-fatal) for a session
 * from the stack_trace_events table
 */
const fetchSessionExceptions = async (sessionId: string, timeRange: { start: string; end: string }) => {
  const formattedStartTime = formatTime(timeRange.start);
  const formattedEndTime = formatTime(timeRange.end);

  const requestBody = {
    dataType: "EXCEPTIONS", // This should map to stack_trace_events table
    timeRange: {
      start: formattedStartTime,
      end: formattedEndTime,
    },
    select: [
      { function: "COL", param: { field: "Timestamp" }, alias: "timestamp" },
      { function: "COL", param: { field: "PulseType" }, alias: "pulseType" },
      { function: "COL", param: { field: "Title" }, alias: "title" },
      { function: "COL", param: { field: "ExceptionMessage" }, alias: "exceptionMessage" },
      { function: "COL", param: { field: "ExceptionType" }, alias: "exceptionType" },
      { function: "COL", param: { field: "ScreenName" }, alias: "screenName" },
      { function: "COL", param: { field: "TraceId" }, alias: "traceId" },
      { function: "COL", param: { field: "SpanId" }, alias: "spanId" },
      { function: "COL", param: { field: "GroupId" }, alias: "groupId" },
    ],
    filters: [
      {
        field: "SessionId",
        operator: "EQ",
        value: [sessionId],
      },
    ],
    orderBy: [{ field: "Timestamp", direction: "ASC" }],
    limit: 1000,
  };

  const dataQuery = API_ROUTES.DATA_QUERY;

  return makeRequest<RawDataResponse>({
    url: `${API_BASE_URL}${dataQuery.apiPath}`,
    init: {
      method: dataQuery.method,
      body: JSON.stringify(requestBody),
    },
  });
};

export const useGetSessionData = ({
  sessionId,
  timeRange,
  enabled = true,
}: GetSessionDataParams) => {
  return useQuery({
    queryKey: ["sessionData", sessionId, timeRange.start, timeRange.end],
    queryFn: async (): Promise<SessionDataResponse> => {
      // Fetch traces, logs, and exceptions in parallel
      const [tracesResponse, logsResponse, exceptionsResponse] = await Promise.all([
        fetchSessionTraces(sessionId, timeRange),
        fetchSessionLogs(sessionId, timeRange),
        fetchSessionExceptions(sessionId, timeRange).catch(() => null), // Don't fail if exceptions table doesn't exist
      ]);

      return {
        traces: tracesResponse?.data || null,
        logs: logsResponse?.data || null,
        exceptions: exceptionsResponse?.data || null,
      };
    },
    refetchOnWindowFocus: false,
    enabled: enabled && !!sessionId,
    staleTime: 0,
  });
};
