import { useMemo } from "react";
import { useGetDataQuery } from "../useGetDataQuery";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { COLUMN_NAME, SpanType } from "../../constants/PulseOtelSemcov";
import {
  UseGetActiveSessionsDataProps,
  ActiveSessionsData,
} from "./useGetActiveSessionsData.interface";

dayjs.extend(utc);

export function useGetActiveSessionsData({
  screenName,
  appVersion,
  osVersion,
  device,
  startTime,
  endTime,
  bucketSize,
  spanType = SpanType.APP_START,
}: UseGetActiveSessionsDataProps): {
  data: ActiveSessionsData;
  isLoading: boolean;
  error: Error | null;
} {

  // Build filters array
  const buildFilters = useMemo(() => {
    const filterArray: Array<{
      field: string;
      operator: "IN" | "EQ";
      value: string[];
    }> = [
      {
        field: COLUMN_NAME.SPAN_TYPE,
        operator: "EQ",
        value: [spanType],
      },
    ];

    if (screenName) {
      filterArray.push({
        field: `SpanAttributes['${SpanType.SCREEN_NAME}']`,
        operator: "IN",
        value: [screenName],
      });
    }

    if (appVersion && appVersion !== "all") {
      filterArray.push({
        field: `ResourceAttributes['${COLUMN_NAME.APP_VERSION}']`,
        operator: "EQ",
        value: [appVersion],
      });
    }

    if (osVersion && osVersion !== "all") {
      filterArray.push({
        field: COLUMN_NAME.OS_VERSION,
        operator: "EQ",
        value: [osVersion],
      });
    }

    if (device && device !== "all") {
      filterArray.push({
        field: COLUMN_NAME.DEVICE_MODEL,
        operator: "EQ",
        value: [device],
      });
    }

    return filterArray;
  }, [screenName, appVersion, osVersion, device, spanType]);

  // Fetch active sessions
  const { data, isLoading } = useGetDataQuery({
    requestBody: {
      dataType: "TRACES",
      timeRange: {
        start: startTime,
        end: endTime,
      },
      select: [
        {
          function: "TIME_BUCKET",
          param: { bucket: bucketSize, field: COLUMN_NAME.TIMESTAMP },
          alias: "t1",
        },
        {
          function: "CUSTOM",
          param: { expression: `uniqCombined(${COLUMN_NAME.SESSION_ID})` },
          alias: "session_count",
        },
      ],
      filters: buildFilters,
      groupBy: ["t1"],
      orderBy: [{ field: "t1", direction: "ASC" }],
    },
    enabled: !!startTime && !!endTime,
  });

  // Transform data and calculate metrics
  const { currentSessions, peakSessions, averageSessions, trendData } =
    useMemo(() => {
      const responseData = data?.data;
      if (
        !responseData ||
        !responseData.rows ||
        responseData.rows.length === 0
      ) {
        return {
          currentSessions: 0,
          peakSessions: 0,
          averageSessions: 0,
          trendData: [],
        };
      }

      const t1Index = responseData.fields.indexOf("t1");
      const sessionCountIndex = responseData.fields.indexOf("session_count");

      const trend = responseData.rows.map((row) => ({
        timestamp: dayjs(row[t1Index]).valueOf(),
        sessions: parseFloat(row[sessionCountIndex]) || 0,
      }));

      // Calculate metrics
      const sessionCounts = trend.map((d) => d.sessions);
      const current = sessionCounts[sessionCounts.length - 1] || 0; // Most recent
      const peak = Math.max(...sessionCounts);
      const average = Math.round(
        sessionCounts.reduce((sum, val) => sum + val, 0) / sessionCounts.length,
      );

      return {
        currentSessions: Math.round(current),
        peakSessions: Math.round(peak),
        averageSessions: average,
        trendData: trend,
      };
    }, [data]);

  const error = null; // You can enhance this to capture errors from queries if needed

  return {
    data: {
      currentSessions,
      peakSessions,
      averageSessions,
      trendData,
    },
    isLoading,
    error,
  };
}

