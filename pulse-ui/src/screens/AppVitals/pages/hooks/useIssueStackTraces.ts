import { useMemo } from "react";
import { useGetDataQuery } from "../../../../hooks";
import { useQueryError } from "../../../../hooks/useQueryError";
import type { DataQueryResponse } from "../../../../hooks/useGetDataQuery/useGetDataQuery.interface";
import { COLUMN_NAME } from "../../../../constants/PulseOtelSemcov";
interface StackTrace {
  timestamp: Date;
  device: string;
  osVersion: string;
  appVersion: string;
  trace: string;
  logId?: string;
}

interface UseIssueStackTracesParams {
  groupId: string;
  startTime?: string;
  endTime?: string;
  limit?: number;
}

export function useIssueStackTraces({
  groupId,
  startTime,
  endTime,
  limit = 10,
}: UseIssueStackTracesParams) {
  // Build filters - filter by GroupId
  const filters = useMemo(() => {
    const filterArray = [];

    // Filter by GroupId
    if (groupId) {
      filterArray.push({
        field: "GroupId",
        operator: "EQ" as const,
        value: [groupId],
      });
    }

    return filterArray.length > 0 ? filterArray : undefined;
  }, [groupId]);

  // Memoize time range to prevent unnecessary re-renders
  const timeRange = useMemo(() => {
    if (startTime && endTime) {
      return {
        start: startTime,
        end: endTime,
      };
    }
    // Default to last 7 days
    return {
      start: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
      end: new Date().toISOString(),
    };
  }, [startTime, endTime]);

  // Memoize request body
  const requestBody = useMemo(
    () => ({
      dataType: "EXCEPTIONS" as const,
      timeRange,
      filters,
      select: [
        {
          function: "COL" as const,
          param: {
            field: "TraceId",
          },
          alias: "trace_id",
        },
        {
          function: "COL" as const,
          param: {
            field: "SpanId",
          },
          alias: "span_id",
        },
        {
          function: "COL" as const,
          param: {
            field: "Timestamp",
          },
          alias: "timestamp",
        },
        {
          function: "COL" as const,
          param: {
            field: "DeviceModel",
          },
          alias: "device",
        },
        {
          function: "COL" as const,
          param: {
            field: "OsVersion",
          },
          alias: "os_version",
        },
        {
          function: "COL" as const,
          param: {
            field: COLUMN_NAME.APP_VERSION,
          },
          alias: "app_version",
        },
        {
          function: "COL" as const,
          param: {
            field: "ExceptionStackTrace",
          },
          alias: "stacktrace",
        },
        {
          function: "COL" as const,
          param: {
            field: "ExceptionMessage",
          },
          alias: "error_message",
        },
        {
          function: "COL" as const,
          param: {
            field: "ExceptionType",
          },
          alias: "error_type",
        },
      ],
      orderBy: [
        {
          field: "timestamp",
          direction: "DESC" as const,
        },
      ],
      limit,
    }),
    [timeRange, filters, limit],
  );

  // Fetch stack traces (individual occurrences) for this GroupId
  const queryResult = useGetDataQuery({
    requestBody,
    enabled: !!groupId,
  });

  const { data } = queryResult;
  const queryState = useQueryError<DataQueryResponse>({ queryResult });

  // Transform API response to StackTrace format
  const stackTraces: StackTrace[] = useMemo(() => {
    const responseData = data?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return [];
    }

    const fields = responseData.fields;
    const traceIdIndex = fields.indexOf("trace_id");
    const spanIdIndex = fields.indexOf("span_id");
    const timestampIndex = fields.indexOf("timestamp");
    const deviceIndex = fields.indexOf("device");
    const osVersionIndex = fields.indexOf("os_version");
    const appVersionIndex = fields.indexOf("app_version");
    const stacktraceIndex = fields.indexOf("stacktrace");
    const errorMessageIndex = fields.indexOf("error_message");

    return responseData.rows.map((row) => {
      const traceId = row[traceIdIndex] || "";
      const spanId = row[spanIdIndex] || "";
      const timestamp = row[timestampIndex] || new Date().toISOString();
      const device = row[deviceIndex] || "Unknown Device";
      const osVersion = row[osVersionIndex] || "Unknown OS";
      const appVersion = row[appVersionIndex] || "Unknown Version";
      const stacktrace = row[stacktraceIndex] || "";
      const errorMessage = row[errorMessageIndex] || "";

      // Use stacktrace if available, otherwise use error message
      const trace = stacktrace || errorMessage || "No stack trace available";

      // Use traceId + spanId as unique identifier
      const logId =
        traceId && spanId
          ? `${traceId}-${spanId}`
          : `log-${Date.now()}-${Math.random()}`;

      return {
        timestamp: new Date(timestamp),
        device,
        osVersion,
        appVersion,
        trace,
        logId,
      };
    });
  }, [data]);

  return {
    stackTraces,
    queryState,
  };
}
