import { useMemo } from "react";
import { FilterField, useGetDataQuery } from "../../../../../../../../hooks";
import { COLUMN_NAME, PulseType } from "../../../../../../../../constants/PulseOtelSemcov";
import dayjs from "dayjs";
import {
  UseGetOsPerformanceParams,
  UseGetOsPerformanceReturn,
  OsPerformanceData,
} from "./useGetOsPerformance.interface";
import { FILTER_MAPPING } from "../../../../../../../../hooks/hooks.interface";

export const useGetOsPerformance = ({
  interactionName,
  startTime,
  endTime,
  enabled = true,
  dashboardFilters,
}: UseGetOsPerformanceParams): UseGetOsPerformanceReturn => {
  
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
        { function: "FROZEN_FRAME" as const, alias: "frozen_frame" },
        { function: "ANR" as const, alias: "anr" },
        { function: "CRASH" as const, alias: "crash" },
        { function: "COL" as const, param: { field: COLUMN_NAME.OS_VERSION }, alias: "osVersion" },
      ],
      groupBy: ["osVersion"],
      filters: requestFilters,
      limit: 10,
    }),
    [startTime, endTime, requestFilters],
  );

  // Fetch OS performance data
  const {
    data,
    isLoading,
    error,
  } = useGetDataQuery({
    requestBody,
    enabled: enabled && !!startTime && !!endTime && !!interactionName,
  });

  // Transform OS performance data
  const osPerformanceData = useMemo(() => {
    const responseData = data?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return {
        crashesByOS: [],
        anrByOS: [],
        frozenFramesByOS: [],
      };
    }

    const frozenFrameIndex = responseData.fields.indexOf("frozen_frame");
    const anrIndex = responseData.fields.indexOf("anr");
    const crashIndex = responseData.fields.indexOf("crash");
    const osVersionIndex = responseData.fields.indexOf("osVersion");

    const crashesByOS = responseData.rows
      .map((row) => ({
        os: String(row[osVersionIndex] || ""),
        crashes: parseFloat(String(row[crashIndex])) || 0,
      }))
      .sort((a, b) => a.crashes - b.crashes);

    const anrByOS = responseData.rows
      .map((row) => ({
        os: String(row[osVersionIndex] || ""),
        anr: parseFloat(String(row[anrIndex])) || 0,
      }))
      .sort((a, b) => a.anr - b.anr);

    const frozenFramesByOS = responseData.rows
      .map((row) => ({
        os: String(row[osVersionIndex] || ""),
        frozenFrames: parseFloat(String(row[frozenFrameIndex])) || 0,
      }))
      .sort((a, b) => a.frozenFrames - b.frozenFrames);

    return {
      crashesByOS,
      anrByOS,
      frozenFramesByOS,
    } as OsPerformanceData;
  }, [data]);

  const isError = !!error || !!data?.error;

  return {
    osPerformanceData,
    isLoading,
    isError,
    error: error || null,
  };
};

