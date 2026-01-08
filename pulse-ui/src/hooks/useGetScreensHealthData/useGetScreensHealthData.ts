import { useMemo } from "react";
import { useGetDataQuery } from "../useGetDataQuery";
import { COLUMN_NAME, STATUS_CODE, PulseType } from "../../constants/PulseOtelSemcov";
import {
  UseGetScreensHealthDataProps,
  ScreenHealthData,
} from "./useGetScreensHealthData.interface";

export function  useGetScreensHealthData({
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
          param: { field: `SpanAttributes['${PulseType.SCREEN_NAME}']` },
          alias: "screen_name",
        },
        {
          function: "CUSTOM",
            param: {
            expression: `countIf(PulseType = '${PulseType.SCREEN_SESSION}')`,
          },
          alias: "session_count",
        },
        {
          function: "CUSTOM",
          param: {
            expression: `countIf(PulseType = '${PulseType.SCREEN_LOAD}')`,
          },
          alias: "load_count",
        },
        {
          function: "CUSTOM",
          param: {
            expression: `sumIf(Duration,PulseType = '${PulseType.SCREEN_SESSION}')`,
          },
          alias: "total_time_spent",
        },
        {
          function: "CUSTOM",
          param: {
            expression: `sumIf(Duration,PulseType = '${PulseType.SCREEN_LOAD}')`,
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
      orderBy: [{field: "load_count", direction: "DESC" }, { field: "session_count", direction: "DESC" }],
      filters: [
        {
          field: "PulseType",
          operator: "IN",
          value: [PulseType.SCREEN_SESSION, PulseType.SCREEN_LOAD],
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
    const sessionCountIndex = fields.indexOf("session_count");
    const loadCountIndex = fields.indexOf("load_count");
    const totalTimeSpentIndex = fields.indexOf("total_time_spent");
    const totalLoadTimeIndex = fields.indexOf("total_load_time");
    const userCountIndex = fields.indexOf("user_count");
    const errorCountIndex = fields.indexOf("error_count");

    return responseData.rows.map((row) => {
      const sessionCount = parseFloat(row[sessionCountIndex]) || 1;
      const loadCount = parseFloat(row[loadCountIndex]) || 1;
      const totalTimeSpent = parseFloat(row[totalTimeSpentIndex]) || 0;
      const totalLoadTime = parseFloat(row[totalLoadTimeIndex]) || 0;
      const errorCount = parseFloat(row[errorCountIndex]) || 0;

      return {
        screenName: row[screenNameIndex],
        avgTimeSpent: Math.round(totalTimeSpent / sessionCount), // Average time per session
        crashRate: (errorCount / sessionCount) * 100 || 0, // Crash rate based on sessions
        loadTime: Math.round(totalLoadTime / loadCount), // Average load time per load
        users: parseInt(row[userCountIndex]) || 0,
        screenType: row[screenNameIndex],
        errorRate: errorCount,
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

