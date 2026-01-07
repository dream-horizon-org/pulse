import { useMemo } from "react";
import { FilterField, useGetDataQuery } from "../../../../../../../../hooks";
import { COLUMN_NAME, PulseType } from "../../../../../../../../constants/PulseOtelSemcov";
import dayjs from "dayjs";
import {
  UseGetReleasePerformanceParams,
  UseGetReleasePerformanceReturn,
  ReleasePerformanceData,
} from "./useGetReleasePerformance.interface";
import { FILTER_MAPPING } from "../../../../../../../../hooks/hooks.interface";

export const useGetReleasePerformance = ({
  interactionName,
  startTime,
  endTime,
  enabled = true,
  dashboardFilters,
}: UseGetReleasePerformanceParams): UseGetReleasePerformanceReturn => {

  const requestFilters: FilterField[] = useMemo(() => {
    const baseFilters: FilterField[] = [
      { field: "PulseType", operator: "EQ", value: [PulseType.INTERACTION] },
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
        { function: "APDEX" as const, alias: "apdex" },
        { function: "CRASH" as const, alias: "crash" },
        { function: "ANR" as const, alias: "anr" },
        { function: "INTERACTION_SUCCESS_COUNT" as const, alias: "success_count" },
        { function: "INTERACTION_ERROR_COUNT" as const, alias: "error_count" },
        { function: "COL" as const, param: { field: COLUMN_NAME.APP_VERSION }, alias: "release" },
      ],
      groupBy: ["release"],
      filters: requestFilters,
    }),
    [startTime, endTime, requestFilters],
  );

  // Fetch release performance data
  const {
    data,
    isLoading,
    error,
  } = useGetDataQuery({
    requestBody,
    enabled: enabled && !!startTime && !!endTime && !!interactionName,
  });

  // Transform release data
  const releaseData = useMemo(() => {
    const responseData = data?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return [] as ReleasePerformanceData[];
    }

    const apdexIndex = responseData.fields.indexOf("apdex");
    const crashIndex = responseData.fields.indexOf("crash");
    const anrIndex = responseData.fields.indexOf("anr");
    const releaseIndex = responseData.fields.indexOf("release");
    const successCountIndex = responseData.fields.indexOf("success_count");
    const errorCountIndex = responseData.fields.indexOf("error_count");

    return responseData.rows.map((row) => ({
      version: String(row[releaseIndex] || ""),
      apdex: parseFloat(String(row[apdexIndex])) || 0,
      crashes:
        (parseFloat(String(row[crashIndex])) /
          (parseFloat(String(row[successCountIndex])) +
            parseFloat(String(row[errorCountIndex])))) *
          100 || 0,
      anr:
        (parseFloat(String(row[anrIndex])) /
          (parseFloat(String(row[successCountIndex])) +
            parseFloat(String(row[errorCountIndex])))) *
          100 || 0,
    })) as ReleasePerformanceData[];
  }, [data]);

  const isError = !!error || !!data?.error;

  return {
    releaseData,
    isLoading,
    isError,
    error: error || null,
  };
};

