import { useMemo } from "react";
import { FilterField, useGetDataQuery } from "../../../../../../../../hooks";
import { COLUMN_NAME, SpanType } from "../../../../../../../../constants/PulseOtelSemcov";
import dayjs from "dayjs";
import {
  UseGetPlatformInsightsParams,
  UseGetPlatformInsightsReturn,
  PlatformInsightsData,
  PlatformDataPoint,
} from "./useGetPlatformInsights.interface";
import { FILTER_MAPPING } from "../../../../../../../../hooks/hooks.interface";

export const useGetPlatformInsights = ({
  interactionName,
  startTime,
  endTime,
  enabled = true,
  dashboardFilters,
}: UseGetPlatformInsightsParams): UseGetPlatformInsightsReturn => {

  const requestFilters: FilterField[] = useMemo(() => {
    const baseFilters: FilterField[] = [
      { field: "SpanType", operator: "EQ", value: [SpanType.INTERACTION] },
    ];

    if (interactionName) {
      baseFilters.push({ field: "SpanName", operator: "EQ", value: [interactionName] });
    }


    Object.entries(FILTER_MAPPING).forEach(([filterKey, fieldName]) => {
      const value = dashboardFilters?.[filterKey as keyof typeof dashboardFilters];
      if (value) {
        baseFilters.push({ field: fieldName, operator: "EQ", value: [value] });
      }
    });

    return baseFilters;
  }, [interactionName, dashboardFilters]);

  // Memoize the request body
  const requestBody = useMemo(
    () => ({
      dataType: "TRACES" as const,
      timeRange: {
        start: dayjs.utc(startTime).toISOString(),
        end: dayjs.utc(endTime).toISOString(),
      },
      select: [
        { function: "INTERACTION_ERROR_COUNT" as const, alias: "error_count" },
        { function: "USER_CATEGORY_POOR" as const, alias: "user_poor" },
        { function: "COL" as const, param: { field: COLUMN_NAME.PLATFORM }, alias: "platform" },
      ],
      groupBy: ["platform"],
      filters: requestFilters,
    }),
    [startTime, endTime, requestFilters],
  );

  // Fetch platform insights data
  const {
    data,
    isLoading,
    error,
  } = useGetDataQuery({
    requestBody,
    enabled: enabled && !!startTime && !!endTime && !!interactionName,
  });

  // Transform platform data
  const platformData = useMemo(() => {
    const responseData = data?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return {
        poorUsersByPlatform: [] as PlatformDataPoint[],
        errorsByPlatform: [] as PlatformDataPoint[],
      };
    }

    const errorCountIndex = responseData.fields.indexOf("error_count");
    const userPoorIndex = responseData.fields.indexOf("user_poor");
    const platformIndex = responseData.fields.indexOf("platform");

    // Helper to normalize empty platform names to "Unknown"
    const normalizePlatformName = (value: unknown): string => {
      const platform = String(value || "").trim();
      return platform === "" ? "Unknown" : platform;
    };

    const poorUsersByPlatform = responseData.rows.map((row) => {
      const poorUsers = parseFloat(String(row[userPoorIndex])) || 0;
      return {
        platform: normalizePlatformName(row[platformIndex]),
        value: poorUsers,
      };
    });

    const errorsByPlatform = responseData.rows.map((row) => {
      const errorCount = parseFloat(String(row[errorCountIndex])) || 0;
      return {
        platform: normalizePlatformName(row[platformIndex]),
        value: errorCount,
      };
    });

    return {
      poorUsersByPlatform,
      errorsByPlatform,
    } as PlatformInsightsData;
  }, [data]);

  const isError = !!error || !!data?.error;

  return {
    platformData,
    isLoading,
    isError,
    error: error || null,
  };
};

