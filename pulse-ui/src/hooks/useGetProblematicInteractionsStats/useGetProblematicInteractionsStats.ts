import { useMemo } from "react";
import dayjs from "dayjs";
import { useGetDataQuery, DataQueryRequestBody, FilterField } from "../useGetDataQuery";
import { COLUMN_NAME, PulseType } from "../../constants/PulseOtelSemcov";
import {
  UseGetProblematicInteractionsStatsParams,
  UseGetProblematicInteractionsStatsReturn,
} from "./useGetProblematicInteractionsStats.interface";
import { FILTER_MAPPING } from "../hooks.interface";

export const useGetProblematicInteractionsStats = ({
  interactionName,
  startTime,
  endTime,
  enabled = true,
  dashboardFilters,
}: UseGetProblematicInteractionsStatsParams): UseGetProblematicInteractionsStatsReturn => {

  const requestFilters: FilterField[] = useMemo(() => {
    const baseFilters: FilterField[] = [
      { field: COLUMN_NAME.PULSE_TYPE, operator: "EQ", value: [PulseType.INTERACTION] },
    ];

    if (interactionName) {
      baseFilters.push({ field: COLUMN_NAME.SPAN_NAME, operator: "EQ", value: [interactionName] });
    }


    Object.entries(FILTER_MAPPING).forEach(([filterKey, fieldName]) => {
      const value = dashboardFilters?.[filterKey as keyof typeof dashboardFilters];
      if (value) {
        baseFilters.push({ field: fieldName, operator: "EQ", value: [value] });
      }
    });

    return baseFilters;
  }, [interactionName, dashboardFilters]);

  const requestBody = useMemo(
    (): DataQueryRequestBody => ({
      dataType: "TRACES",
      timeRange: {
        start: dayjs.utc(startTime).toISOString(),
        end: dayjs.utc(endTime).toISOString(),
      },
      select: [
        {
          function: "INTERACTION_SUCCESS_COUNT",
          alias: "success_count",
        },
        { function: "INTERACTION_ERROR_COUNT", alias: "error_count" },
        { function: "CRASH", alias: "crash" },
        { function: "DURATION_P95", alias: "p95" },
      ],
      filters: requestFilters,
    }),
    [startTime, endTime, requestFilters],
  );

  const { data, isLoading, error } = useGetDataQuery({
    requestBody,
    enabled: enabled && !!interactionName && !!startTime && !!endTime,
  });

  const stats = useMemo(() => {
    if (!data?.data || !data.data.rows || data.data.rows.length === 0) {
      return {
        total: 0,
        completed: 0,
        errored: "0%",
        crashed: "0%",
        latency: 0,
      };
    }

    const fields = data.data.fields;
    const successCountIndex = fields.indexOf("success_count");
    const errorCountIndex = fields.indexOf("error_count");
    const crashIndex = fields.indexOf("crash");
    const p95Index = fields.indexOf("p95");

    const row = data.data.rows[0];
    const successCount = parseFloat(String(row[successCountIndex] || "0")) || 0;
    const errorCount = parseFloat(String(row[errorCountIndex] || "0")) || 0;
    const crash = parseFloat(String(row[crashIndex] || "0")) || 0;
    const p95 = parseFloat(String(row[p95Index] || "0")) || 0;

    const total = successCount + errorCount;
    const completed = successCount;
    const crashed = total > 0 ? ((crash / total) * 100).toFixed(2) : "0.00";
    const errored = total > 0 ? ((errorCount / total) * 100).toFixed(2) : "0.00";
    const latency = p95;

    return { total, completed, crashed, errored, latency };
  }, [data]);

  return {
    stats,
    isLoading,
    isError: !!error || !!data?.error,
    error: error || data?.error || null,
  };
};

