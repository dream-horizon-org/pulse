import { useMemo } from "react";
import { FilterField, useGetDataQuery } from "../../../../../../../../hooks";
import { COLUMN_NAME, SpanType } from "../../../../../../../../constants/PulseOtelSemcov";
import dayjs from "dayjs";
import {
  UseGetRegionalInsightsParams,
  UseGetRegionalInsightsReturn,
  RegionalInsightsData,
  RegionalDataPoint,
} from "./useGetRegionalInsights.interface";
import { FILTER_MAPPING } from "../../../../../../../../hooks/hooks.interface";

export const useGetRegionalInsights = ({
  interactionName,
  startTime,
  endTime,
  enabled = true,
  dashboardFilters,
}: UseGetRegionalInsightsParams): UseGetRegionalInsightsReturn => {

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
        { function: "INTERACTION_SUCCESS_COUNT" as const, alias: "success_count" },
        { function: "INTERACTION_ERROR_COUNT" as const, alias: "error_count" },
        { function: "USER_CATEGORY_POOR" as const, alias: "user_poor" },
        { function: "COL" as const, param: { field: COLUMN_NAME.STATE }, alias: "region" },
      ],
      groupBy: ["region"],
      filters: requestFilters,
    }),
    [startTime, endTime, requestFilters],
  );

  // Fetch regional insights data
  const {
    data,
    isLoading,
    error,
  } = useGetDataQuery({
    requestBody,
    enabled: enabled && !!startTime && !!endTime && !!interactionName,
  });

  // Transform regional data
  const regionalData = useMemo(() => {
    const responseData = data?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return {
        errorRateByRegion: [] as RegionalDataPoint[],
        poorUsersPercentageByRegion: [] as RegionalDataPoint[],
      };
    }

    const successCountIndex = responseData.fields.indexOf("success_count");
    const errorCountIndex = responseData.fields.indexOf("error_count");
    const userPoorIndex = responseData.fields.indexOf("user_poor");
    const regionIndex = responseData.fields.indexOf("region");

    // Helper to normalize empty region names to "Unknown"
    const normalizeRegionName = (value: unknown): string => {
      const region = String(value || "").trim();
      return region === "" ? "Unknown" : region;
    };

    const errorRateByRegion = responseData.rows.map((row) => {
      const successCount = parseFloat(String(row[successCountIndex])) || 0;
      const errorCount = parseFloat(String(row[errorCountIndex])) || 0;
      const totalCount = successCount + errorCount;
      const errorRate =
        totalCount > 0
          ? Number(((errorCount / totalCount) * 100).toFixed(1))
          : 0;
      return {
        name: normalizeRegionName(row[regionIndex]),
        value: errorRate,
      };
    });

    const poorUsersPercentageByRegion = responseData.rows.map((row) => {
      const userPoor = parseFloat(String(row[userPoorIndex])) || 0;
      const successCount = parseFloat(String(row[successCountIndex])) || 0;
      const totalCount = successCount;
      const poorPercentage =
        totalCount > 0 ? Number(((userPoor / totalCount) * 100).toFixed(2)) : 0;
      return {
        name: normalizeRegionName(row[regionIndex]),
        value: poorPercentage,
      };
    });

    return {
      errorRateByRegion,
      poorUsersPercentageByRegion,
    } as RegionalInsightsData;
  }, [data]);

  const isError = !!error || !!data?.error;

  return {
    regionalData,
    isLoading,
    isError,
    error: error || null,
  };
};

