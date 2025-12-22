import { useMemo } from "react";
import { useGetDataQuery } from "../../../../hooks";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { getTimeBucketSize } from "../../../../utils/TimeBucketUtil";
import { SpanType, COLUMN_NAME } from "../../../../constants/PulseOtelSemcov";

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
        field: `SpanAttributes['${SpanType.SCREEN_NAME}']`,
        operator: "IN",
        value: [screenName],
      },
      {
        field: COLUMN_NAME.SPAN_TYPE,
        operator: "IN",
        value: [SpanType.SCREEN_SESSION, SpanType.SCREEN_LOAD],
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
          param: { field: `SpanAttributes['${SpanType.SCREEN_NAME}']` },
          alias: "screen_name",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: `sumIf(${COLUMN_NAME.DURATION},${COLUMN_NAME.SPAN_TYPE} = '${SpanType.SCREEN_SESSION}')`,
          },
          alias: "total_time_spent",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: `sumIf(${COLUMN_NAME.DURATION},${COLUMN_NAME.SPAN_TYPE} = '${SpanType.SCREEN_LOAD}')`,
          },
          alias: "total_load_time",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: `countIf(${COLUMN_NAME.SPAN_TYPE} = '${SpanType.SCREEN_SESSION}')`,
          },
          alias: "session_count",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: `countIf(${COLUMN_NAME.SPAN_TYPE} = '${SpanType.SCREEN_LOAD}')`,
          },
          alias: "load_count",
        },
        {
          function: "CUSTOM" as const,
          param: {
            // Note: TRACES table has direct UserId column (not nested in ResourceAttributes)
            expression: `uniqCombined(${COLUMN_NAME.USER_ID})`,
          },
          alias: "unique_users",
        },
        {
          function: "CUSTOM" as const,
          param: {
            // Note: TRACES table has direct SessionId column (not nested in ResourceAttributes)
            expression: `uniqCombined(${COLUMN_NAME.SESSION_ID})`,
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
    isLoading,
    error: queryError,
  } = useGetDataQuery({
    requestBody,
    enabled: !!screenName && !!formattedStartTime && !!formattedEndTime,
  });

  // Transform data
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
    const uniqueUsersIndex = responseData.fields.indexOf("unique_users");
    const uniqueSessionsIndex = responseData.fields.indexOf("unique_sessions");

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
    let totalUniqueUsers = 0;
    let totalUniqueSessions = 0;

    responseData.rows.forEach((row) => {
      const timestamp = dayjs(row[t1Index]).valueOf();
      const timeSpent = parseFloat(row[totalTimeSpentIndex]) || 0;
      const loadTime = parseFloat(row[totalLoadTimeIndex]) || 0;
      const sessions = parseFloat(row[sessionCountIndex]) || 0;
      const loads = parseFloat(row[loadCountIndex]) || 0;
      const uniqueUsers = parseFloat(row[uniqueUsersIndex]) || 0;
      const uniqueSessions = parseFloat(row[uniqueSessionsIndex]) || 0;

      totalTimeSpentSum += timeSpent;
      totalLoadTimeSum += loadTime;
      totalSessions += sessions;
      totalLoads += loads;
      // Sum unique users/sessions from all buckets (approximation)
      totalUniqueUsers += uniqueUsers;
      totalUniqueSessions += uniqueSessions;

      const avgTimeSpentVal = sessions > 0 ? timeSpent / sessions / 1_000_000_000 : 0; // Convert nanoseconds to seconds
      const avgLoadTimeVal = loads > 0 ? loadTime / loads / 1_000_000_000 : 0; // Convert nanoseconds to seconds

      trend.push({
        timestamp,
        avgTimeSpent: Math.round(avgTimeSpentVal * 100) / 100, // Round to 2 decimals for better precision
        avgLoadTime: Math.round(avgLoadTimeVal * 100) / 100, // Round to 2 decimals
        sessionCount: Math.round(sessions),
      });
    });
    
    // Calculate overall averages - return null if no valid data
    const avgTimeSpent =
      totalSessions > 0
        ? Math.round((totalTimeSpentSum / totalSessions / 1_000_000_000) * 100) / 100
        : null;
    const avgLoadTime =
      totalLoads > 0
        ? Math.round((totalLoadTimeSum / totalLoads / 1_000_000_000) * 100) / 100
        : null;
        
    const hasData = totalSessions > 0 || totalLoads > 0 || trend.length > 0;
    
    return {
      avgTimeSpent,
      avgLoadTime,
      totalSessions: Math.round(totalUniqueSessions), // Use unique session count for crash-free calculations
      totalUsers: Math.round(totalUniqueUsers),
      hasData,
      trendData: trend,
    };
  }, [data]);
  return {
    data: transformedData,
    isLoading,
    error: queryError as Error | null,
  };
}
