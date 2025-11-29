import { useMemo } from "react";
import { useGetDataQuery } from "../useGetDataQuery";
import { COLUMN_NAME, STATUS_CODE, SpanType } from "../../constants/PulseOtelSemcov";
import {
  UseGetScreensHealthDataProps,
  ScreenHealthData,
} from "./useGetScreensHealthData.interface";

export function useGetScreensHealthData({
  startTime,
  endTime,
  limit = 5,
}: UseGetScreensHealthDataProps): {
  data: ScreenHealthData[];
  isLoading: boolean;
  error: Error | null;
} {

  // Fetch top screens data
  const { data, isLoading } = useGetDataQuery({
    requestBody: {
      dataType: "TRACES",
      timeRange: {
        start: startTime,
        end: endTime,
      },
      select: [
        {
          function: "COL",
          param: { field: `SpanAttributes['${SpanType.SCREEN_NAME}']` },
          alias: "screen_name",
        },
        {
          function: "CUSTOM",
          param: { expression: "COUNT()" },
          alias: "screen_count",
        },
        {
          function: "CUSTOM",
          param: {
            expression: `sumIf(Duration,SpanType = '${SpanType.SCREEN_SESSION}')`,
          },
          alias: "total_time_spent",
        },
        {
          function: "CUSTOM",
          param: {
            expression: `sumIf(Duration,SpanType = '${SpanType.SCREEN_LOAD}')`,
          },
          alias: "total_load_time",
        },
        {
          function: "CUSTOM",
          param: { expression: `uniqCombined(${COLUMN_NAME.USER_ID})` },
          alias: "user_count",
        },
        {
          function: "CUSTOM",
          param: {
            expression: `countIf(StatusCode = '${STATUS_CODE.ERROR}')`,
          },
          alias: "error_count",
        },
      ],
      groupBy: ["screen_name"],
      orderBy: [{ field: "screen_count", direction: "DESC" }],
      filters: [
        {
          field: "SpanType",
          operator: "IN",
          value: [SpanType.SCREEN_SESSION, SpanType.SCREEN_LOAD],
        },
      ],
      limit,
    },
    enabled: !!startTime && !!endTime,
  });

  // Transform API response to screen data
  const screensData = useMemo<ScreenHealthData[]>(() => {
    const responseData = data?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return [];
    }

    const fields = responseData.fields;
    const screenNameIndex = fields.indexOf("screen_name");
    const screenCountIndex = fields.indexOf("screen_count");
    const totalTimeSpentIndex = fields.indexOf("total_time_spent");
    const totalLoadTimeIndex = fields.indexOf("total_load_time");
    const userCountIndex = fields.indexOf("user_count");
    const errorCountIndex = fields.indexOf("error_count");

    return responseData.rows.map((row) => {
      const screenCount = parseFloat(row[screenCountIndex]) || 1;
      const totalTimeSpent = parseFloat(row[totalTimeSpentIndex]) || 0;
      const totalLoadTime = parseFloat(row[totalLoadTimeIndex]) || 0;

      return {
        screenName: row[screenNameIndex],
        avgTimeSpent: Math.round(totalTimeSpent / screenCount), // Average time per session
        crashRate: (parseFloat(row[errorCountIndex]) / screenCount) * 100 || 0,
        loadTime: Math.round(totalLoadTime / screenCount), // Average load time
        users: parseInt(row[userCountIndex]) || 0,
        screenType: row[screenNameIndex],
        errorRate: parseFloat(row[errorCountIndex]) || 0,
      };
    });
  }, [data]);

  const error = null; // You can enhance this to capture errors from queries if needed

  return {
    data: screensData,
    isLoading,
    error,
  };
}

