import { STATUS_CODE, PulseType } from "../constants/PulseOtelSemcov";
import {
  GetScreensResponse,
  Screen,
} from "../screens/ScreenList/ScreenList.interface";
import { useGetDataQuery } from "./useGetDataQuery";
import { useMemo } from "react";

interface UseGetScreensParams {
  startTime: string;
  endTime: string;
  searchStr?: string;
  enabled?: boolean;
}

export const useGetScreens = ({
  startTime,
  endTime,
  searchStr = "",
  enabled = true,
}: UseGetScreensParams) => {
  // Memoize the top screens request body to prevent infinite loops
  const topScreensRequestBody = useMemo(
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
          param: { expression: "COUNT()" },
          alias: "screen_count",
        },
      ],
      groupBy: ["screen_name"],
      orderBy: [
        {
          field: "screen_count",
          direction: "DESC" as const,
        },
      ],
      limit: 15,
    }),
    [startTime, endTime],
  );

  // Fetch top 15 screens
  const { data: topScreensData, isLoading: isLoadingTopScreens } =
    useGetDataQuery({
      requestBody: topScreensRequestBody,
      enabled: enabled && !!startTime && !!endTime,
    });

  // Extract screen names from top screens
  const screenNames = useMemo(() => {
    if (!topScreensData?.data?.rows || topScreensData.data.rows.length === 0) {
      return [];
    }
    const fields = topScreensData.data.fields;
    const screenNameIndex = fields.indexOf("screen_name");
    return topScreensData.data.rows
      .map((row) => row[screenNameIndex])
      .filter((name): name is string => Boolean(name && name.trim()));
  }, [topScreensData]);

  // Filter screen names by search string
  const filteredScreenNames = useMemo(() => {
    if (!searchStr) return screenNames;
    return screenNames.filter((name) =>
      name.toLowerCase().includes(searchStr.toLowerCase()),
    );
  }, [screenNames, searchStr]);

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
          value: filteredScreenNames,
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
    [startTime, endTime, filteredScreenNames],
  );

  // Fetch screen details for all filtered screens
  const { data: screenDetailsData, isLoading: isLoadingDetails } =
    useGetDataQuery({
      requestBody: screenDetailsRequestBody,
      enabled:
        enabled && filteredScreenNames.length > 0 && !!startTime && !!endTime,
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

  const isLoading = isLoadingTopScreens || isLoadingDetails;
  const isError = false; // Add error handling if needed

  // Memoize the response data to prevent unnecessary re-renders
  const responseData = useMemo(
    () =>
      ({
        screens,
        totalScreens: screens.length,
      }) as GetScreensResponse,
    [screens],
  );

  return {
    data: responseData,
    isLoading,
    isError,
    refetch: () => {}, // Add refetch if needed
  };
};

export type { GetScreensResponse };
