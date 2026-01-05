import { useMemo } from "react";
import { useGetDataQuery } from "../../../../../hooks/useGetDataQuery";
import {
  UseGetNetworkIssuesByProviderParams,
  UseGetNetworkIssuesByProviderReturn,
} from "./useGetNetworkIssuesByProvider.interface";

export const useGetNetworkIssuesByProvider = ({
  method,
  url,
  startTime,
  endTime,
  enabled = true,
  additionalFilters = [],
}: UseGetNetworkIssuesByProviderParams): UseGetNetworkIssuesByProviderReturn => {
  const isEnabled = enabled && !!method && !!url && !!startTime && !!endTime;

  // Memoize request body for connection errors (network.0)
  const connErrorRequestBody = useMemo(
    () => ({
      dataType: "TRACES" as const,
      timeRange: { start: startTime, end: endTime },
      select: [
        { function: "CUSTOM" as const, param: { expression: "count()" }, alias: "conn_error" },
        { function: "CUSTOM" as const, param: { expression: "ResourceAttributes['network.provider']" }, alias: "network_provider" },
      ],
      groupBy: ["network_provider"],
      filters: [
        { field: "PulseType", operator: "EQ" as const, value: ["network.0"] },
        { field: "SpanAttributes['http.method']", operator: "EQ" as const, value: [method] },
        { field: "SpanAttributes['http.url']", operator: "EQ" as const, value: [url] },
        ...additionalFilters,
      ],
      orderBy: [{ field: "conn_error", direction: "ASC" as const }],
      limit: 10,
    }),
    [startTime, endTime, method, url, additionalFilters]
  );

  // Memoize request body for 4xx errors
  const error4xxRequestBody = useMemo(
    () => ({
      dataType: "TRACES" as const,
      timeRange: { start: startTime, end: endTime },
      select: [
        { function: "CUSTOM" as const, param: { expression: "count()" }, alias: "4xx" },
        { function: "CUSTOM" as const, param: { expression: "ResourceAttributes['network.provider']" }, alias: "network_provider" },
      ],
      groupBy: ["network_provider"],
      filters: [
        { field: "PulseType", operator: "LIKE" as const, value: ["network.4%"] },
        { field: "SpanAttributes['http.method']", operator: "EQ" as const, value: [method] },
        { field: "SpanAttributes['http.url']", operator: "EQ" as const, value: [url] },
        ...additionalFilters,
      ],
      orderBy: [{ field: "4xx", direction: "ASC" as const }],
      limit: 10,
    }),
    [startTime, endTime, method, url, additionalFilters]
  );

  // Memoize request body for 5xx errors
  const error5xxRequestBody = useMemo(
    () => ({
      dataType: "TRACES" as const,
      timeRange: { start: startTime, end: endTime },
      select: [
        { function: "CUSTOM" as const, param: { expression: "count()" }, alias: "5xx" },
        { function: "CUSTOM" as const, param: { expression: "ResourceAttributes['network.provider']" }, alias: "network_provider" },
      ],
      groupBy: ["network_provider"],
      filters: [
        { field: "PulseType", operator: "LIKE" as const, value: ["network.5%"] },
        { field: "SpanAttributes['http.method']", operator: "EQ" as const, value: [method] },
        { field: "SpanAttributes['http.url']", operator: "EQ" as const, value: [url] },
        ...additionalFilters,
      ],
      orderBy: [{ field: "5xx", direction: "ASC" as const }],
      limit: 10,
    }),
    [startTime, endTime, method, url, additionalFilters]
  );

  // Query for connection errors
  const {
    data: connErrorData,
    isLoading: isLoadingConn,
    error: connError,
  } = useGetDataQuery({
    requestBody: connErrorRequestBody,
    enabled: isEnabled,
  });

  // Query for 4xx errors
  const {
    data: error4xxData,
    isLoading: isLoading4xx,
    error: error4xx,
  } = useGetDataQuery({
    requestBody: error4xxRequestBody,
    enabled: isEnabled,
  });

  // Query for 5xx errors
  const {
    data: error5xxData,
    isLoading: isLoading5xx,
    error: error5xx,
  } = useGetDataQuery({
    requestBody: error5xxRequestBody,
    enabled: isEnabled,
  });

  // Transform connection errors (network.0)
  const connectionTimeoutErrorsByNetwork = useMemo(() => {
    if (!connErrorData?.data?.rows || connErrorData.data.rows.length === 0) return [];
    const fields = connErrorData.data.fields;
    const connErrorIndex = fields.indexOf("conn_error");
    const networkProviderIndex = fields.indexOf("network_provider");
    return connErrorData.data.rows
      .map((row) => ({
        networkProvider: row[networkProviderIndex] || "Unknown",
        errors: parseFloat(String(row[connErrorIndex])) || 0,
      }))
      .filter((item) => item.errors > 0);
  }, [connErrorData]);

  // Transform 4xx errors
  const error4xxByNetwork = useMemo(() => {
    if (!error4xxData?.data?.rows || error4xxData.data.rows.length === 0) return [];
    const fields = error4xxData.data.fields;
    const error4xxIndex = fields.indexOf("4xx");
    const networkProviderIndex = fields.indexOf("network_provider");
    return error4xxData.data.rows
      .map((row) => ({
        networkProvider: row[networkProviderIndex] || "Unknown",
        errors: parseFloat(String(row[error4xxIndex])) || 0,
      }))
      .filter((item) => item.errors > 0);
  }, [error4xxData]);

  // Transform 5xx errors
  const error5xxByNetwork = useMemo(() => {
    if (!error5xxData?.data?.rows || error5xxData.data.rows.length === 0) return [];
    const fields = error5xxData.data.fields;
    const error5xxIndex = fields.indexOf("5xx");
    const networkProviderIndex = fields.indexOf("network_provider");
    return error5xxData.data.rows
      .map((row) => ({
        networkProvider: row[networkProviderIndex] || "Unknown",
        errors: parseFloat(String(row[error5xxIndex])) || 0,
      }))
      .filter((item) => item.errors > 0);
  }, [error5xxData]);

  const isError = !!connError || !!error4xx || !!error5xx;
  const error = connError || error4xx || error5xx || null;

  return {
    data: {
      connectionTimeoutErrorsByNetwork,
      error4xxByNetwork,
      error5xxByNetwork,
    },
    isLoading: isLoadingConn || isLoading4xx || isLoading5xx,
    isError,
    error,
  };
};

