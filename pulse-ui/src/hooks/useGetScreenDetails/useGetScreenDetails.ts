import { STATUS_CODE, PulseType } from "../../constants/PulseOtelSemcov";
import { Screen } from "../../screens/ScreenList/ScreenList.interface";
import { useGetDataQuery } from "../useGetDataQuery";
import { useMemo } from "react";

interface UseGetScreenDetailsParams {
  screenNames: string[];
  startTime: string;
  endTime: string;
  enabled?: boolean;
}

export const useGetScreenDetails = ({
  screenNames,
  startTime,
  endTime,
  enabled = true,
}: UseGetScreenDetailsParams) => {
  // Memoize the screen details request body to prevent infinite loops
  const screenDetailsRequestBody = useMemo(
    () => ({
      dataType: "TRACES" as const,
      timeRange: {
        start: startTime,
        end: endTime,
      },
      select: [
        {
          function: "COL" as const,
          param: { field: `SpanAttributes['${PulseType.SCREEN_NAME}']` },
          alias: "screen_name",
        },
        {
          function: "CUSTOM" as const,
          param: { expression: "sumIf(Duration,PulseType = 'screen_session')" },
          alias: "total_time_spent",
        },
        {
          function: "CUSTOM" as const,
          param: { expression: "sumIf(Duration,PulseType = 'screen_load')" },
          alias: "total_load_time",
        },
        {
          function: "CUSTOM" as const,
          param: { expression: "countIf(PulseType = 'screen_session')" },
          alias: "session_count",
        },
        {
          function: "CUSTOM" as const,
          param: { expression: "countIf(PulseType = 'screen_load')" },
          alias: "load_count",
        },
        {
          function: "CUSTOM" as const,
          param: { expression: "uniqCombined(UserId)" },
          alias: "user_count",
        },
        {
          function: "CUSTOM" as const,
          param: { expression: `countIf(StatusCode != '${STATUS_CODE.ERROR}')` },
          alias: "success_count",
        },
        {
          function: "CUSTOM" as const,
          param: { expression: `countIf(StatusCode = '${STATUS_CODE.ERROR}')` },
          alias: "error_count",
        },
        {
          function: "CUSTOM" as const,
          param: { expression: "COUNT()" },
          alias: "screen_count",
        },
      ],
      filters: [
        {
          field: `SpanAttributes['${PulseType.SCREEN_NAME}']`,
          operator: "IN" as const,
          value: screenNames,
        },
        {
          field: "PulseType",
          operator: "IN" as const,
          value: ["screen_session", "screen_load"],
        },
      ],
      groupBy: ["screen_name"],
      orderBy: [
        {
          field: "screen_count",
          direction: "DESC" as const,
        },
      ],
    }),
    [startTime, endTime, screenNames],
  );

  // Fetch screen details for all filtered screens
  const {
    data: screenDetailsData,
    isLoading,
    isError,
  } = useGetDataQuery({
    requestBody: screenDetailsRequestBody,
    enabled: enabled && screenNames.length > 0 && !!startTime && !!endTime,
  });

  // Transform API response to Screen format
  const screens = useMemo(() => {
    if (
      !screenDetailsData?.data?.rows ||
      screenDetailsData.data.rows.length === 0
    ) {
      return [];
    }

    const fields = screenDetailsData.data.fields;
    const screenNameIndex = fields.indexOf("screen_name");
    const totalTimeSpentIndex = fields.indexOf("total_time_spent");
    const totalLoadTimeIndex = fields.indexOf("total_load_time");
    const sessionCountIndex = fields.indexOf("session_count");
    const loadCountIndex = fields.indexOf("load_count");
    const userCountIndex = fields.indexOf("user_count");
    const errorCountIndex = fields.indexOf("error_count");
    const screenCountIndex = fields.indexOf("screen_count");

    return screenDetailsData.data.rows.map((row, index) => {
      const screenName = row[screenNameIndex] || "";
      const totalTimeSpent = parseFloat(row[totalTimeSpentIndex]) || 0;
      const totalLoadTime = parseFloat(row[totalLoadTimeIndex]) || 0;
      const sessionCount = parseFloat(row[sessionCountIndex]) || 1;
      const loadCount = parseFloat(row[loadCountIndex]) || 1;
      const userCount = parseFloat(row[userCountIndex]) || 0;
      const errorCount = parseFloat(row[errorCountIndex]) || 0;
      const totalCount = parseFloat(row[screenCountIndex]) || 1;

      return {
        id: `screen-${index}`,
        screenName,
        description: "",
        status: "Active",
        avgTimeSpent: Math.round(totalTimeSpent / sessionCount),
        loadTime: Math.round(totalLoadTime / loadCount),
        users: Math.round(userCount),
        errorRate: totalCount > 0 ? (errorCount / totalCount) * 100 : 0,
      } as Screen & {
        avgTimeSpent: number;
        loadTime: number;
        users: number;
        errorRate: number;
      };
    });
  }, [screenDetailsData]);

  // Create a map of screen name to screen details for easy lookup
  const screensMap = useMemo(() => {
    const map: Record<
      string,
      Screen & {
        avgTimeSpent: number;
        loadTime: number;
        users: number;
        errorRate: number;
      }
    > = {};
    screens.forEach((screen) => {
      map[screen.screenName] = screen;
    });
    return map;
  }, [screens]);

  return {
    screens,
    screensMap,
    isLoading,
    isError,
  };
};
