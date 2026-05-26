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
  const dataType = "TRACES";

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
        field: COLUMN_NAME.SCREEN_NAME,
        operator: "IN",
        value: [screenName!],
      });
    } else {
      // User Engagement page: LOGS with session.start
      filterArray.push({
        field: COLUMN_NAME.PULSE_TYPE,
        operator: "EQ",
        value: [PulseType.APP_START],
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

  // Dedicated last-5-min query for the "Current" metric
  const now = useMemo(() => dayjs().utc(), []);
  const { data: currentData, isLoading: isLoadingCurrent } = useGetDataQuery({
    requestBody: {
      dataType,
      timeRange: {
        start: now.subtract(5, "minute").toISOString(),
        end: now.toISOString(),
      },
      select: [
        {
          function: "CUSTOM",
          param: {
            expression: `uniq(nullIf(${COLUMN_NAME.SESSION_ID}, ''))`,
          },
          alias: "session_count",
        },
      ],
      filters: buildFilters,
    },
    enabled: !!startTime && !!endTime,
  });

  // Fetch active sessions
  const { data, isLoading: isLoadingTrend } = useGetDataQuery({
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
          param: {
            expression: `uniq(nullIf(${COLUMN_NAME.SESSION_ID}, ''))`,
          },
          alias: "session_count",
        },
      ],
      filters: buildFilters,
      groupBy: ["t1"],
      orderBy: [{ field: "t1", direction: "ASC" }],
    },
    enabled: !!startTime && !!endTime,
  });

  // Derive current sessions from dedicated 5-min query
  const currentSessions = useMemo(() => {
    const responseData = currentData?.data;
    if (!responseData?.rows || responseData.rows.length === 0) return null;
    const idx = responseData.fields.indexOf("session_count");
    return Math.round(parseFloat(responseData.rows[0][idx]) || 0);
  }, [currentData]);

  // Transform trend data and derive peak/average
  const { peakSessions, averageSessions, trendData, hasData } = useMemo(() => {
    const responseData = data?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return {
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

    const sessionCounts = trend.map((d) => d.sessions);
    const peak = Math.max(...sessionCounts);
    const average = Math.round(
      sessionCounts.reduce((sum, val) => sum + val, 0) / sessionCounts.length,
    );

    return {
      peakSessions: Math.round(peak),
      averageSessions: average,
      trendData: trend,
      hasData: true,
    };
  }, [data]);

  const error = null;
  const isLoading = isLoadingCurrent || isLoadingTrend;

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
