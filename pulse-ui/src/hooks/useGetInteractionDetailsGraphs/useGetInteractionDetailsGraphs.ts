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
        apdex: null,
        errorRate: null,
        p50: null,
        p95: null,
        frozenFrameRate: null,
        crashRate: null,
        anrRate: null,
        networkErrorRate: null,
        excellentUsersPercentage: null,
        goodUsersPercentage: null,
        averageUsersPercentage: null,
        poorUsersPercentage: null,
        hasData: false,
      } as InteractionDetailsMetricsData;
    }

    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return {
        apdex: null,
        errorRate: null,
        p50: null,
        p95: null,
        frozenFrameRate: null,
        crashRate: null,
        anrRate: null,
        networkErrorRate: null,
        excellentUsersPercentage: null,
        goodUsersPercentage: null,
        averageUsersPercentage: null,
        poorUsersPercentage: null,
        hasData: false,
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
        apdex: null,
        errorRate: null,
        p50: null,
        p95: null,
        frozenFrameRate: null,
        crashRate: null,
        anrRate: null,
        networkErrorRate: null,
        excellentUsersPercentage: null,
        goodUsersPercentage: null,
        averageUsersPercentage: null,
        poorUsersPercentage: null,
        hasData: false,
      } as InteractionDetailsMetricsData;
    }

    const row = responseData.rows[0];
    
    // Calculate totals to determine if we have meaningful data
    const totalUsers =
      (parseFloat(row[userExcellentIndex]) || 0) +
      (parseFloat(row[userGoodIndex]) || 0) +
      (parseFloat(row[userAvgIndex]) || 0) +
      (parseFloat(row[userPoorIndex]) || 0);
    
    const successCount = parseFloat(row[successCountIndex]) || 0;
    const errorCount = parseFloat(row[errorCountIndex]) || 0;
    const totalRequests = successCount + errorCount;

    const totalFrames =
      (parseFloat(row[unanalysedFrameIndex]) || 0) +
      (parseFloat(row[analysedFrameIndex]) || 0);

    // If there's no meaningful data (no requests, no users), return null values
    if (totalRequests === 0 && totalUsers === 0) {
      return {
        apdex: null,
        errorRate: null,
        p50: null,
        p95: null,
        frozenFrameRate: null,
        crashRate: null,
        anrRate: null,
        networkErrorRate: null,
        excellentUsersPercentage: null,
        goodUsersPercentage: null,
        averageUsersPercentage: null,
        poorUsersPercentage: null,
        hasData: false,
      } as InteractionDetailsMetricsData;
    }

    // Calculate user percentages (null if no users)
    const excellentUsersPercentage =
      totalUsers > 0
        ? ((parseFloat(row[userExcellentIndex]) || 0) / totalUsers) * 100
        : null;
    const goodUsersPercentage =
      totalUsers > 0 
        ? ((parseFloat(row[userGoodIndex]) || 0) / totalUsers) * 100 
        : null;
    const averageUsersPercentage =
      totalUsers > 0 
        ? ((parseFloat(row[userAvgIndex]) || 0) / totalUsers) * 100 
        : null;
    const poorUsersPercentage =
      totalUsers > 0 
        ? ((parseFloat(row[userPoorIndex]) || 0) / totalUsers) * 100 
        : null;

    // Calculate rate metrics (null if no requests)
    const apdexValue = parseFloat(row[apdexIndex]);
    const p50Value = parseFloat(row[p50Index]);
    const p95Value = parseFloat(row[p95Index]);

    return {
      apdex: totalRequests > 0 ? (apdexValue || 0) : null,
      errorRate: totalRequests > 0 
        ? (errorCount / totalRequests) * 100 
        : null,
      p50: totalRequests > 0 ? (p50Value || 0) : null,
      p95: totalRequests > 0 ? (p95Value || 0) : null,
      frozenFrameRate: totalFrames > 0
        ? ((parseFloat(row[frozenFrameIndex]) || 0) / totalFrames) * 100
        : null,
      crashRate: totalRequests > 0
        ? ((parseFloat(row[crashIndex]) || 0) / totalRequests) * 100
        : null,
      anrRate: totalRequests > 0
        ? ((parseFloat(row[anrIndex]) || 0) / totalRequests) * 100
        : null,
      excellentUsersPercentage: excellentUsersPercentage !== null 
        ? excellentUsersPercentage.toFixed(2) + "%" 
        : null,
      goodUsersPercentage: goodUsersPercentage !== null 
        ? goodUsersPercentage.toFixed(2) + "%" 
        : null,
      averageUsersPercentage: averageUsersPercentage !== null 
        ? averageUsersPercentage.toFixed(2) + "%" 
        : null,
      poorUsersPercentage: poorUsersPercentage !== null 
        ? poorUsersPercentage.toFixed(2) + "%" 
        : null,
      networkErrorRate: totalRequests > 0
        ? (((parseFloat(row[net0Index]) || 0) +
            (parseFloat(row[net4xxIndex]) || 0) +
            (parseFloat(row[net5xxIndex]) || 0)) / totalRequests) * 100
        : null,
      hasData: totalRequests > 0 || totalUsers > 0,
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

