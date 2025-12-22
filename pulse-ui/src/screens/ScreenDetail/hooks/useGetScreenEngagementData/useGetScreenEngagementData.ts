import { useMemo } from "react";
import { useGetDataQuery } from "../../../../hooks";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { getTimeBucketSize } from "../../../../utils/TimeBucketUtil";
import { SpanType } from "../../../../constants/PulseOtelSemcov";

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
  avgTimeSpent: number;
  avgLoadTime: number;
  totalSessions: number;
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
        field: "SpanType",
        operator: "IN",
        value: ["screen_session", "screen_load"],
      },
    ];

    if (appVersion && appVersion !== "all") {
      filterArray.push({
        field: "ResourceAttributes['app.version']",
        operator: "EQ",
        value: [appVersion],
      });
    }

    if (osVersion && osVersion !== "all") {
      filterArray.push({
        field: "ResourceAttributes['os.version']",
        operator: "EQ",
        value: [osVersion],
      });
    }

    if (device && device !== "all") {
      filterArray.push({
        field: "ResourceAttributes['device.model']",
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
            expression: "sumIf(Duration,SpanType = 'screen_session')",
          },
          alias: "total_time_spent",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: "sumIf(Duration,SpanType = 'screen_load')",
          },
          alias: "total_load_time",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: "countIf(SpanType = 'screen_session')",
          },
          alias: "session_count",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: "countIf(SpanType = 'screen_load')",
          },
          alias: "load_count",
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

      const avgTimeSpent = sessions > 0 ? timeSpent / sessions / 1_000_000_000 : 0; // Convert nanoseconds to seconds
      const avgLoadTime = loads > 0 ? loadTime / loads / 1_000_000_000 : 0; // Convert nanoseconds to seconds

      trend.push({
        timestamp,
        avgTimeSpent: Math.round(avgTimeSpent * 10) / 10, // Round to 1 decimal
        avgLoadTime: avgLoadTime,
        sessionCount: Math.round(sessions),
      });
    });
    const avgTimeSpent =
      totalSessions > 0
        ? Math.round((totalTimeSpentSum / totalSessions / 1_000_000_000) * 10) / 10
        : 0;
    const avgLoadTime =
      totalLoads > 0
        ? Math.round((totalLoadTimeSum / totalLoads / 1_000_000_000) * 10) / 10
        : 0;
    return {
      avgTimeSpent,
      avgLoadTime,
      totalSessions: Math.round(totalSessions),
      trendData: trend,
    };
  }, [data]);
  return {
    data: transformedData,
    isLoading,
    error: queryError as Error | null,
  };
}
