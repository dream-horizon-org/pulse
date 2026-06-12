import { useMemo } from "react";
import { useGetDataQuery } from "../../../../hooks";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { getTimeBucketSize } from "../../../../utils/TimeBucketUtil";
import { PulseType, COLUMN_NAME } from "../../../../constants/PulseOtelSemcov";
import { PERCENTILE_VALUE } from "../../../../constants/Constants.interface";
import { getPercentileExpression } from "../../../../utils/queryUtil";

dayjs.extend(utc);

interface UseGetScreenEngagementDataProps {
  screenName: string;
  startTime: string;
  endTime: string;
  appVersion?: string;
  osVersion?: string;
  device?: string;
}

interface TransformedData {
  avgTimeSpent: number | null;
  avgLoadTime: number | null;
  tti_p95: number | null;
  tti_p50: number | null;
  totalSessions: number;
  totalUsers: number;
  hasData: boolean;
  trendData: Array<{
    timestamp: number;
    avgTimeSpent: number;
    avgLoadTime: number;
    sessionCount: number;
  }>;
}

export function useGetScreenEngagementData({
  screenName,
  startTime,
  endTime,
  appVersion,
  osVersion,
  device,
}: UseGetScreenEngagementDataProps): {
  data: TransformedData | null;
  isLoading: boolean;
  error: Error | null;
} {
  // Determine bucket size based on time range using utility
  const bucketSize = useMemo(() => {
    return getTimeBucketSize(startTime, endTime);
  }, [startTime, endTime]);

  // Build filters array
  const filters = useMemo(() => {
    const filterArray: Array<{
      field: string;
      operator: "IN" | "EQ";
      value: string[];
    }> = [
      {
        field: COLUMN_NAME.SCREEN_NAME,
        operator: "IN",
        value: [screenName],
      },
      {
        field: COLUMN_NAME.PULSE_TYPE,
        operator: "IN",
        value: [
          PulseType.SCREEN_SESSION,
          PulseType.SCREEN_LOAD,
          PulseType.SCREEN_INTERACTIVE,
        ],
      },
    ];

    // Use MATERIALIZED columns from otel_traces for filters
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
  }, [screenName, appVersion, osVersion, device]);

  // Convert time strings to ISO format if needed
  const formattedStartTime = useMemo(() => {
    if (!startTime) return "";
    // If already in ISO format, return as is
    if (startTime.includes("T") || startTime.includes("Z")) {
      return startTime;
    }
    // Convert "YYYY-MM-DD HH:mm:ss" to ISO format
    return dayjs.utc(startTime).toISOString();
  }, [startTime]);

  const formattedEndTime = useMemo(() => {
    if (!endTime) return "";
    // If already in ISO format, return as is
    if (endTime.includes("T") || endTime.includes("Z")) {
      return endTime;
    }
    // Convert "YYYY-MM-DD HH:mm:ss" to ISO format
    return dayjs.utc(endTime).toISOString();
  }, [endTime]);

  // Build request body
  const requestBody = useMemo(
    () => ({
      dataType: "TRACES" as const,
      timeRange: {
        start: formattedStartTime,
        end: formattedEndTime,
      },
      select: [
        {
          function: "TIME_BUCKET" as const,
          param: { bucket: bucketSize, field: "Timestamp" },
          alias: "t1",
        },
        {
          function: "COL" as const,
          param: { field: COLUMN_NAME.SCREEN_NAME },
          alias: "screen_name",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: `sumIf(${COLUMN_NAME.DURATION},${COLUMN_NAME.PULSE_TYPE} = '${PulseType.SCREEN_SESSION}')`,
          },
          alias: "total_time_spent",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: `sumIf(${COLUMN_NAME.DURATION},${COLUMN_NAME.PULSE_TYPE} = '${PulseType.SCREEN_LOAD}')`,
          },
          alias: "total_load_time",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: `countIf(${COLUMN_NAME.PULSE_TYPE} = '${PulseType.SCREEN_SESSION}')`,
          },
          alias: "session_count",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: `countIf(${COLUMN_NAME.PULSE_TYPE} = '${PulseType.SCREEN_LOAD}')`,
          },
          alias: "load_count",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: `uniq(nullIf(${COLUMN_NAME.INSTALLATION_ID}, ''))`,
          },
          alias: "unique_users",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: `uniq(nullIf(${COLUMN_NAME.SESSION_ID}, ''))`,
          },
          alias: "unique_sessions",
        },
      ],
      filters,
      groupBy: ["t1", "screen_name"],
      orderBy: [
        {
          field: "t1",
          direction: "ASC" as const,
        },
      ],
    }),
    [formattedStartTime, formattedEndTime, bucketSize, filters],
  );

  const {
    data,
    isLoading: isLoadingTrend,
    error: queryError,
  } = useGetDataQuery({
    requestBody,
    enabled: !!screenName && !!formattedStartTime && !!formattedEndTime,
  });

  // Separate non-bucketed query for accurate total unique users/sessions
  const totalsRequestBody = useMemo(
    () => ({
      dataType: "TRACES" as const,
      timeRange: {
        start: formattedStartTime,
        end: formattedEndTime,
      },
      select: [
        {
          function: "CUSTOM" as const,
          param: {
            expression: `uniq(nullIf(${COLUMN_NAME.INSTALLATION_ID}, ''))`,
          },
          alias: "unique_users",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: `uniq(nullIf(${COLUMN_NAME.SESSION_ID}, ''))`,
          },
          alias: "unique_sessions",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: getPercentileExpression(
              PERCENTILE_VALUE.P95,
              COLUMN_NAME.DURATION,
              `${COLUMN_NAME.PULSE_TYPE} = '${PulseType.SCREEN_INTERACTIVE}'`,
            ),
          },
          alias: "tti_p95",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: getPercentileExpression(
              PERCENTILE_VALUE.P50,
              COLUMN_NAME.DURATION,
              `${COLUMN_NAME.PULSE_TYPE} = '${PulseType.SCREEN_INTERACTIVE}'`,
            ),
          },
          alias: "tti_p50",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: `countIf(${COLUMN_NAME.PULSE_TYPE} = '${PulseType.SCREEN_INTERACTIVE}')`,
          },
          alias: "tti_count",
        },
      ],
      filters,
    }),
    [formattedStartTime, formattedEndTime, filters],
  );

  const {
    data: totalsData,
    isLoading: isLoadingTotals,
    error: totalsError,
  } = useGetDataQuery({
    requestBody: totalsRequestBody,
    enabled: !!screenName && !!formattedStartTime && !!formattedEndTime,
  });

  const isLoading = isLoadingTrend || isLoadingTotals;

  // Transform trend data from bucketed query; use totals query for accurate unique counts
  const transformedData = useMemo<TransformedData | null>(() => {
    const responseData = data?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return null;
    }

    const t1Index = responseData.fields.indexOf("t1");
    const totalTimeSpentIndex = responseData.fields.indexOf("total_time_spent");
    const totalLoadTimeIndex = responseData.fields.indexOf("total_load_time");
    const sessionCountIndex = responseData.fields.indexOf("session_count");
    const loadCountIndex = responseData.fields.indexOf("load_count");

    const trend: Array<{
      timestamp: number;
      avgTimeSpent: number;
      avgLoadTime: number;
      sessionCount: number;
    }> = [];

    let totalTimeSpentSum = 0;
    let totalLoadTimeSum = 0;
    let totalSessions = 0;
    let totalLoads = 0;

    responseData.rows.forEach((row) => {
      const timestamp = dayjs(row[t1Index]).valueOf();
      const timeSpent = parseFloat(row[totalTimeSpentIndex]) || 0;
      const loadTime = parseFloat(row[totalLoadTimeIndex]) || 0;
      const sessions = parseFloat(row[sessionCountIndex]) || 0;
      const loads = parseFloat(row[loadCountIndex]) || 0;

      totalTimeSpentSum += timeSpent;
      totalLoadTimeSum += loadTime;
      totalSessions += sessions;
      totalLoads += loads;

      const avgTimeSpentVal =
        sessions > 0 ? timeSpent / sessions / 1_000_000_000 : 0;
      const avgLoadTimeVal = loads > 0 ? loadTime / loads / 1_000_000_000 : 0;

      trend.push({
        timestamp,
        avgTimeSpent: Math.round(avgTimeSpentVal * 100) / 100,
        avgLoadTime: Math.round(avgLoadTimeVal * 100) / 100,
        sessionCount: Math.round(sessions),
      });
    });

    // Accurate unique user/session totals and TTI p95 from the non-bucketed query
    const totalsResponse = totalsData?.data;
    let totalUniqueUsers = 0;
    let totalUniqueSessions = 0;
    let overallTtiP95: number | null = null;
    let overallTtiP50: number | null = null;
    let totalTtiCount = 0;
    if (totalsResponse?.rows && totalsResponse.rows.length > 0) {
      const usersIdx = totalsResponse.fields.indexOf("unique_users");
      const sessionsIdx = totalsResponse.fields.indexOf("unique_sessions");
      const ttiP95Idx = totalsResponse.fields.indexOf("tti_p95");
      const ttiP50Idx = totalsResponse.fields.indexOf("tti_p50");
      const ttiCountIdx = totalsResponse.fields.indexOf("tti_count");
      totalUniqueUsers = parseFloat(totalsResponse.rows[0][usersIdx]) || 0;
      totalUniqueSessions =
        parseFloat(totalsResponse.rows[0][sessionsIdx]) || 0;
      totalTtiCount = parseFloat(totalsResponse.rows[0][ttiCountIdx]) || 0;
      if (totalTtiCount > 0) {
        const p95 = parseFloat(totalsResponse.rows[0][ttiP95Idx]);
        const p50 = parseFloat(totalsResponse.rows[0][ttiP50Idx]);
        overallTtiP95 = Number.isFinite(p95) ? p95 : null;
        overallTtiP50 = Number.isFinite(p50) ? p50 : null;
      }
    }

    const avgTimeSpent =
      totalUniqueSessions > 0
        ? Math.round(
            (totalTimeSpentSum / totalUniqueSessions / 1_000_000_000) * 100,
          ) / 100
        : null;
    const avgLoadTime =
      totalLoads > 0
        ? Math.round((totalLoadTimeSum / totalLoads / 1_000_000_000) * 100) /
          100
        : null;
    const tti_p95 =
      overallTtiP95 !== null ? Math.round((overallTtiP95 / 1_000_000_000) * 100) / 100 : null;
    const tti_p50 =
      overallTtiP50 !== null ? Math.round((overallTtiP50 / 1_000_000_000) * 100) / 100 : null;

    const hasData =
      totalSessions > 0 ||
      totalLoads > 0 ||
      totalTtiCount > 0 ||
      trend.length > 0;

    return {
      avgTimeSpent,
      avgLoadTime,
      tti_p95,
      tti_p50,
      totalSessions: Math.round(totalUniqueSessions),
      totalUsers: Math.round(totalUniqueUsers),
      hasData,
      trendData: trend,
    };
  }, [data, totalsData]);
  return {
    data: transformedData,
    isLoading,
    error: (queryError || totalsError) as Error | null,
  };
}
