import { useMemo } from "react";
import { useGetDataQuery } from "../useGetDataQuery";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { COLUMN_NAME, PulseType } from "../../constants/PulseOtelSemcov";
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
}: UseGetActiveSessionsDataProps): {
  data: ActiveSessionsData;
  isLoading: boolean;
  error: Error | null;
} {

  // Determine data source based on whether screenName is provided
  // - With screenName: Use TRACES with screen_session/screen_load (screen-specific sessions)
  // - Without screenName: Use LOGS with session.start (overall app sessions)
  const useTracesTable = !!screenName;
  const dataType = useTracesTable ? "TRACES" : "LOGS";

  // Build filters array
  const buildFilters = useMemo(() => {
    const filterArray: Array<{
      field: string;
      operator: "IN" | "EQ";
      value: string[];
    }> = [];

    if (useTracesTable) {
      // Screen Detail page: TRACES with screen_session/screen_load
      filterArray.push({
        field: COLUMN_NAME.PULSE_TYPE,
        operator: "IN",
        value: [PulseType.SCREEN_SESSION, PulseType.SCREEN_LOAD],
      });
      filterArray.push({
        field: `SpanAttributes['${PulseType.SCREEN_NAME}']`,
        operator: "IN",
        value: [screenName!],
      });
    } else {
      // User Engagement page: LOGS with session.start
      filterArray.push({
        field: COLUMN_NAME.PULSE_TYPE,
        operator: "EQ",
        value: [PulseType.SESSION_START],
      });
    }

    if (appVersion && appVersion !== "all") {
      filterArray.push({
        field: COLUMN_NAME.APP_VERSION,
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
  }, [screenName, appVersion, osVersion, device, useTracesTable]);

  // Fetch active sessions
  const { data, isLoading } = useGetDataQuery({
    requestBody: {
      dataType,
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
  const { currentSessions, peakSessions, averageSessions, trendData, hasData } =
    useMemo(() => {
      const responseData = data?.data;
      if (
        !responseData ||
        !responseData.rows ||
        responseData.rows.length === 0
      ) {
        return {
          currentSessions: null,
          peakSessions: null,
          averageSessions: null,
          trendData: [],
          hasData: false,
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
        hasData: true,
      };
    }, [data]);

  const error = null; // You can enhance this to capture errors from queries if needed

  return {
    data: {
      currentSessions,
      peakSessions,
      averageSessions,
      trendData,
      hasData,
    },
    isLoading,
    error,
  };
}

