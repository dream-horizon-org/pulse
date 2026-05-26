import { useMemo } from "react";
import { useGetDataQuery, getDataQueryStatus } from "../useGetDataQuery";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { COLUMN_NAME, PulseType } from "../../constants/PulseOtelSemcov";
import {
  UseGetActiveSessionsDataProps,
  ActiveSessionsData,
  ActiveSessionsLoadingState,
  ActiveSessionsFailedState,
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
  loading: ActiveSessionsLoadingState;
  failed: ActiveSessionsFailedState;
  error: Error | null;
} {
  const useTracesTable = !!screenName;
  const dataType = "TRACES";

  const buildFilters = useMemo(() => {
    const filterArray: Array<{
      field: string;
      operator: "IN" | "EQ";
      value: string[];
    }> = [];

    if (useTracesTable) {
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

  const now = useMemo(() => dayjs().utc(), []);
  const currentQuery = useGetDataQuery({
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

  const trendQuery = useGetDataQuery({
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

  const currentData = currentQuery.data;
  const data = trendQuery.data;

  const currentStatus = getDataQueryStatus(currentQuery);
  const trendStatus = getDataQueryStatus(trendQuery);

  const currentSessions = useMemo(() => {
    const responseData = currentData?.data;
    if (!responseData?.rows || responseData.rows.length === 0) return null;
    const idx = responseData.fields.indexOf("session_count");
    return Math.round(parseFloat(responseData.rows[0][idx]) || 0);
  }, [currentData]);

  const { peakSessions, averageSessions, trendData } = useMemo(() => {
    const responseData = data?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return {
        peakSessions: null,
        averageSessions: null,
        trendData: [],
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
    };
  }, [data]);

  const hasData = currentSessions !== null || trendData.length > 0;

  const loading: ActiveSessionsLoadingState = {
    current: currentStatus.loading,
    trend: trendStatus.loading,
  };

  const failed: ActiveSessionsFailedState = {
    current: currentStatus.failed,
    trend: trendStatus.failed,
  };

  const isLoading = loading.current || loading.trend;
  const error = null;

  return {
    data: {
      currentSessions,
      peakSessions,
      averageSessions,
      trendData,
      hasData,
    },
    isLoading,
    loading,
    failed,
    error,
  };
}
