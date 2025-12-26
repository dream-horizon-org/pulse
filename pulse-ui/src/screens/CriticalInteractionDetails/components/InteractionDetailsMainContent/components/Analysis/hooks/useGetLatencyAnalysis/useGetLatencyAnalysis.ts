import { useMemo } from "react";
import { FilterField, useGetDataQuery } from "../../../../../../../../hooks";
import { COLUMN_NAME, PulseType } from "../../../../../../../../constants/PulseOtelSemcov";
import dayjs from "dayjs";
import {
  UseGetLatencyAnalysisParams,
  UseGetLatencyAnalysisReturn,
} from "./useGetLatencyAnalysis.interface";
import { FILTER_MAPPING } from "../../../../../../../../hooks/hooks.interface";

export const useGetLatencyAnalysis = ({
  interactionName,
  startTime,
  endTime,
  enabled = true,
  dashboardFilters,
}: UseGetLatencyAnalysisParams): UseGetLatencyAnalysisReturn => {
  const isEnabled = enabled && !!startTime && !!endTime && !!interactionName;

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

  // Memoize request body for network latency
  const networkRequestBody = useMemo(
    () => ({
      dataType: "TRACES" as const,
      timeRange: {
        start: dayjs.utc(startTime).toISOString(),
        end: dayjs.utc(endTime).toISOString(),
      },
      select: [
        { function: "DURATION_P95" as const, alias: "latency" },
        {
          function: "COL" as const,
          param: { field: COLUMN_NAME.NETWORK_PROVIDER },
          alias: "network_provider",
        },
      ],
      groupBy: ["network_provider"],
      filters: requestFilters,
      limit: 10,
    }),
    [startTime, endTime, requestFilters],
  );

  // Memoize request body for device latency
  const deviceRequestBody = useMemo(
    () => ({
      dataType: "TRACES" as const,
      timeRange: {
        start: dayjs.utc(startTime).toISOString(),
        end: dayjs.utc(endTime).toISOString(),
      },
      select: [
        { function: "DURATION_P95" as const, alias: "latency" },
        {
          function: "COL" as const,
          param: { field: COLUMN_NAME.DEVICE_MODEL },
          alias: "device_model",
        },
      ],
      groupBy: ["device_model"],
      filters: requestFilters,
      limit: 10,
    }),
    [startTime, endTime, requestFilters],
  );

  // Memoize request body for OS latency
  const osRequestBody = useMemo(
    () => ({
      dataType: "TRACES" as const,
      timeRange: {
        start: dayjs.utc(startTime).toISOString(),
        end: dayjs.utc(endTime).toISOString(),
      },
      select: [
        { function: "DURATION_P95" as const, alias: "latency" },
        { function: "COL" as const, param: { field: COLUMN_NAME.OS_VERSION }, alias: "os_version" },
      ],
      groupBy: ["os_version"],
      filters: requestFilters,
      limit: 10,
    }),
    [startTime, endTime, requestFilters],
  );

  // Fetch latency by network provider
  const {
    data: networkData,
    isLoading: isLoadingNetwork,
    error: errorNetwork,
  } = useGetDataQuery({
    requestBody: networkRequestBody,
    enabled: isEnabled,
  });

  // Fetch latency by device model
  const {
    data: deviceData,
    isLoading: isLoadingDevice,
    error: errorDevice,
  } = useGetDataQuery({
    requestBody: deviceRequestBody,
    enabled: isEnabled,
  });

  // Fetch latency by OS version
  const {
    data: osData,
    isLoading: isLoadingOs,
    error: errorOs,
  } = useGetDataQuery({
    requestBody: osRequestBody,
    enabled: isEnabled,
  });

  // Transform network latency data
  const latencyByNetwork = useMemo(() => {
    const responseData = networkData?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return [];
    }

    const latencyIndex = responseData.fields.indexOf("latency");
    const networkProviderIndex =
      responseData.fields.indexOf("network_provider");

    return responseData.rows
      .map((row) => ({
        networkProvider: String(row[networkProviderIndex] || ""),
        latency: parseFloat(String(row[latencyIndex])) || 0,
      }))
      .sort((a, b) => a.latency - b.latency);
  }, [networkData]);

  // Transform device latency data
  const latencyByDevice = useMemo(() => {
    const responseData = deviceData?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return [];
    }

    const latencyIndex = responseData.fields.indexOf("latency");
    const deviceModelIndex = responseData.fields.indexOf("device_model");

    return responseData.rows
      .map((row) => ({
        device: String(row[deviceModelIndex] || ""),
        latency: parseFloat(String(row[latencyIndex])) || 0,
      }))
      .sort((a, b) => a.latency - b.latency);
  }, [deviceData]);

  // Transform OS latency data
  const latencyByOS = useMemo(() => {
    const responseData = osData?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return [];
    }

    const latencyIndex = responseData.fields.indexOf("latency");
    const osVersionIndex = responseData.fields.indexOf("os_version");

    return responseData.rows
      .map((row) => ({
        os: String(row[osVersionIndex] || ""),
        latency: parseFloat(String(row[latencyIndex])) || 0,
      }))
      .sort((a, b) => a.latency - b.latency);
  }, [osData]);

  const isError =
    !!errorNetwork ||
    !!errorDevice ||
    !!errorOs ||
    !!networkData?.error ||
    !!deviceData?.error ||
    !!osData?.error;

  const error =
    errorNetwork || errorDevice || errorOs || networkData?.error || deviceData?.error || osData?.error || null;

  return {
    latencyAnalysisData: {
      latencyByNetwork,
      latencyByDevice,
      latencyByOS,
    },
    isLoading: isLoadingNetwork || isLoadingDevice || isLoadingOs,
    isError,
    error,
  };
};

