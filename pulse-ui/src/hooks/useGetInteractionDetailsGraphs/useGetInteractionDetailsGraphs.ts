import { useMemo } from "react";
import { FilterField, useGetDataQuery } from "../useGetDataQuery";
import { COLUMN_NAME, SpanType } from "../../constants/PulseOtelSemcov";
import dayjs from "dayjs";
import { getTimeBucketSize } from "../../utils";
import { showNotification } from "@mantine/notifications";
import {
  UseGetInteractionDetailsGraphsParams,
  UseGetInteractionDetailsGraphsReturn,
  InteractionDetailsGraphsData,
  InteractionDetailsMetricsData,
} from "./useGetInteractionDetailsGraphs.interface";
import { FILTER_MAPPING } from "../hooks.interface";

export const useGetInteractionDetailsGraphs = ({
  interactionName,
  startTime,
  endTime,
  enabled = true,
  dashboardFilters
}: UseGetInteractionDetailsGraphsParams): UseGetInteractionDetailsGraphsReturn => {
  // Build filters
  const requestFilters: FilterField[] = useMemo(() => {
    const baseFilters: FilterField[] = [
      { field: COLUMN_NAME.SPAN_TYPE, operator: "EQ", value: [SpanType.INTERACTION] },
    ];

    if (interactionName) {
      baseFilters.push({
        field: COLUMN_NAME.SPAN_NAME,
        operator: "EQ",
        value: [interactionName],
      });
    }

  
    Object.entries(FILTER_MAPPING).forEach(([filterKey, fieldName]) => {
      const value = dashboardFilters?.[filterKey as keyof typeof dashboardFilters];
      if (value) {
        baseFilters.push({ field: fieldName, operator: "EQ", value: [value] });
      }
    });

    return baseFilters;
  }, [interactionName, dashboardFilters]);

  // Memoize the graph data request body
  const graphDataRequestBody = useMemo(
    () => ({
      dataType: "TRACES" as const,
      timeRange: {
        start: dayjs.utc(startTime || "").toISOString(),
        end: dayjs.utc(endTime || "").toISOString(),
      },
      select: [
        {
          function: "TIME_BUCKET" as const,
          param: {
            bucket: getTimeBucketSize(
              startTime?.toString() || "",
              endTime?.toString() || "",
            ),
            field: COLUMN_NAME.TIMESTAMP,
          },
          alias: "t1",
        },
        { function: "APDEX" as const, alias: "apdex" },
        { function: "INTERACTION_SUCCESS_COUNT" as const, alias: "success_count" },
        { function: "INTERACTION_ERROR_COUNT" as const, alias: "error_count" },
        { function: "USER_CATEGORY_AVERAGE" as const, alias: "user_avg" },
        { function: "USER_CATEGORY_GOOD" as const, alias: "user_good" },
        { function: "USER_CATEGORY_EXCELLENT" as const, alias: "user_excellent" },
        { function: "USER_CATEGORY_POOR" as const, alias: "user_poor" },
      ],
      filters: requestFilters,
      groupBy: ["t1"],
      orderBy: [{ field: "t1", direction: "ASC" as const }],
    }),
    [startTime, endTime, requestFilters],
  );

  // Memoize the metrics request body
  const metricsRequestBody = useMemo(
    () => ({
      dataType: "TRACES" as const,
      timeRange: {
        start: dayjs.utc(startTime || "").toISOString(),
        end: dayjs.utc(endTime || "").toISOString(),
      },
      select: [
        { function: "APDEX" as const, alias: "apdex" },
        { function: "INTERACTION_SUCCESS_COUNT" as const, alias: "success_count" },
        { function: "INTERACTION_ERROR_COUNT" as const, alias: "error_count" },
        { function: "DURATION_P50" as const, alias: "p50" },
        { function: "DURATION_P95" as const, alias: "p95" },
        { function: "FROZEN_FRAME" as const, alias: "frozen_frame" },
        { function: "UNANALYSED_FRAME" as const, alias: "unanalysed_frame" },
        { function: "ANALYSED_FRAME" as const, alias: "analysed_frame" },
        { function: "CRASH" as const, alias: "crash" },
        { function: "ANR" as const, alias: "anr" },
        { function: "NET_0" as const, alias: "net_0" },
        { function: "NET_2XX" as const, alias: "net_2xx" },
        { function: "NET_4XX" as const, alias: "net_4xx" },
        { function: "NET_5XX" as const, alias: "net_5xx" },
        { function: "USER_CATEGORY_EXCELLENT" as const, alias: "user_excellent" },
        { function: "USER_CATEGORY_GOOD" as const, alias: "user_good" },
        { function: "USER_CATEGORY_AVERAGE" as const, alias: "user_avg" },
        { function: "USER_CATEGORY_POOR" as const, alias: "user_poor" },
      ],
      filters: requestFilters,
    }),
    [startTime, endTime, requestFilters],
  );

  // Fetch graph data
  const {
    data: graphDataResponse,
    isLoading: isLoadingGraphData,
    error: graphDataError,
  } = useGetDataQuery({
    requestBody: graphDataRequestBody,
    enabled: enabled && !!startTime && !!endTime,
  });

  // Fetch metrics data
  const {
    data: metricsDataResponse,
    isLoading: isLoadingMetrics,
    error: metricsError,
  } = useGetDataQuery({
    requestBody: metricsRequestBody,
    enabled: enabled && !!startTime && !!endTime,
  });

  // Transform graph data
  const graphData = useMemo(() => {
    const responseData = graphDataResponse?.data;

    // Check error and return empty array if there is an error
    if (graphDataError || graphDataResponse?.error) {
      showNotification({
        title: "Error fetching interaction details graphs",
        message:
          graphDataError?.message ||
          graphDataResponse?.error?.message ||
          "Unknown error",
        color: "red",
      });
      return [] as InteractionDetailsGraphsData[];
    }

    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return [] as InteractionDetailsGraphsData[];
    }

    const t1Index = responseData.fields.indexOf("t1");
    const apdexIndex = responseData.fields.indexOf("apdex");
    const successCountIndex = responseData.fields.indexOf("success_count");
    const errorCountIndex = responseData.fields.indexOf("error_count");
    const userAvgIndex = responseData.fields.indexOf("user_avg");
    const userGoodIndex = responseData.fields.indexOf("user_good");
    const userExcellentIndex = responseData.fields.indexOf("user_excellent");
    const userPoorIndex = responseData.fields.indexOf("user_poor");

    return responseData.rows.map((row) => {
      return {
        timestamp: dayjs(row[t1Index]).valueOf(),
        apdex: parseFloat(row[apdexIndex]) || 0,
        errorRate:
          (parseFloat(row[errorCountIndex]) /
            (parseFloat(row[successCountIndex]) +
              parseFloat(row[errorCountIndex]))) *
            100 || 0,
        userAvg: parseFloat(row[userAvgIndex]) || 0,
        userGood: parseFloat(row[userGoodIndex]) || 0,
        userExcellent: parseFloat(row[userExcellentIndex]) || 0,
        userPoor: parseFloat(row[userPoorIndex]) || 0,
      };
    });
  }, [graphDataResponse, graphDataError]);

  // Transform metrics data
  const metrics = useMemo(() => {
    const responseData = metricsDataResponse?.data;

    if (metricsError || metricsDataResponse?.error) {
      showNotification({
        title: "Error fetching interaction details metrics",
        message:
          metricsError?.message ||
          metricsDataResponse?.error?.message ||
          "Unknown error",
        color: "red",
      });
      return {
        apdex: 0,
        errorRate: 0,
        p50: 0,
        p95: 0,
        frozenFrameRate: 0,
        crashRate: 0,
        anrRate: 0,
        networkErrorRate: 0,
        excellentUsersPercentage: "0%",
        goodUsersPercentage: "0%",
        averageUsersPercentage: "0%",
        poorUsersPercentage: "0%",
      } as InteractionDetailsMetricsData;
    }

    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return {
        apdex: 0,
        errorRate: 0,
        p50: 0,
        p95: 0,
        frozenFrameRate: 0,
        crashRate: 0,
        anrRate: 0,
        networkErrorRate: 0,
        excellentUsersPercentage: "0%",
        goodUsersPercentage: "0%",
        averageUsersPercentage: "0%",
        poorUsersPercentage: "0%",
      } as InteractionDetailsMetricsData;
    }

    const apdexIndex = responseData.fields.indexOf("apdex");
    const p50Index = responseData.fields.indexOf("p50");
    const p95Index = responseData.fields.indexOf("p95");
    const frozenFrameIndex = responseData.fields.indexOf("frozen_frame");
    const unanalysedFrameIndex =
      responseData.fields.indexOf("unanalysed_frame");
    const analysedFrameIndex = responseData.fields.indexOf("analysed_frame");
    const crashIndex = responseData.fields.indexOf("crash");
    const anrIndex = responseData.fields.indexOf("anr");
    const net0Index = responseData.fields.indexOf("net_0");
    const net4xxIndex = responseData.fields.indexOf("net_4xx");
    const net5xxIndex = responseData.fields.indexOf("net_5xx");
    const successCountIndex = responseData.fields.indexOf("success_count");
    const errorCountIndex = responseData.fields.indexOf("error_count");
    const userExcellentIndex = responseData.fields.indexOf("user_excellent");
    const userGoodIndex = responseData.fields.indexOf("user_good");
    const userAvgIndex = responseData.fields.indexOf("user_avg");
    const userPoorIndex = responseData.fields.indexOf("user_poor");

    if (responseData.rows.length === 0) {
      return {
        apdex: 0,
        errorRate: 0,
        p50: 0,
        p95: 0,
        frozenFrameRate: 0,
        crashRate: 0,
        anrRate: 0,
        networkErrorRate: 0,
        excellentUsersPercentage: "0%",
        goodUsersPercentage: "0%",
        averageUsersPercentage: "0%",
        poorUsersPercentage: "0%",
      } as InteractionDetailsMetricsData;
    }

    const row = responseData.rows[0];
    const totalUsers =
      parseFloat(row[userExcellentIndex]) +
      parseFloat(row[userGoodIndex]) +
      parseFloat(row[userAvgIndex]) +
      parseFloat(row[userPoorIndex]);
    const excellentUsersPercentage =
      totalUsers > 0
        ? (parseFloat(row[userExcellentIndex]) / totalUsers) * 100
        : 0;
    const goodUsersPercentage =
      totalUsers > 0 ? (parseFloat(row[userGoodIndex]) / totalUsers) * 100 : 0;
    const averageUsersPercentage =
      totalUsers > 0 ? (parseFloat(row[userAvgIndex]) / totalUsers) * 100 : 0;
    const poorUsersPercentage =
      totalUsers > 0 ? (parseFloat(row[userPoorIndex]) / totalUsers) * 100 : 0;

    const totalFrames =
      parseFloat(row[unanalysedFrameIndex]) +
      parseFloat(row[analysedFrameIndex]);

    return {
      apdex: parseFloat(row[apdexIndex]) || 0,
      errorRate:
        (parseFloat(row[errorCountIndex]) /
          (parseFloat(row[successCountIndex]) +
            parseFloat(row[errorCountIndex]))) *
          100 || 0,
      p50: parseFloat(row[p50Index]) || 0,
      p95: parseFloat(row[p95Index]) || 0,
      frozenFrameRate:
        totalFrames > 0
          ? (parseFloat(row[frozenFrameIndex]) / totalFrames) * 100
          : 0,
      crashRate:
        (parseFloat(row[crashIndex]) /
          (parseFloat(row[successCountIndex]) +
            parseFloat(row[errorCountIndex]))) *
          100 || 0,
      anrRate:
        (parseFloat(row[anrIndex]) /
          (parseFloat(row[successCountIndex]) +
            parseFloat(row[errorCountIndex]))) *
          100 || 0,
      excellentUsersPercentage: excellentUsersPercentage.toFixed(2) + "%",
      goodUsersPercentage: goodUsersPercentage.toFixed(2) + "%",
      averageUsersPercentage: averageUsersPercentage.toFixed(2) + "%",
      poorUsersPercentage: poorUsersPercentage.toFixed(2) + "%",
      networkErrorRate:
        ((parseFloat(row[net0Index]) +
          parseFloat(row[net4xxIndex]) +
          parseFloat(row[net5xxIndex])) /
          (parseFloat(row[successCountIndex]) +
            parseFloat(row[errorCountIndex]))) *
          100 || 0,
    } as InteractionDetailsMetricsData;
  }, [metricsDataResponse, metricsError]);

  const isLoading = isLoadingGraphData || isLoadingMetrics;
  const isError = !!graphDataError || !!metricsError;

  return {
    graphData,
    metrics,
    isLoading,
    isError,
  };
};

