import { useMemo } from "react";
import { FilterField, useGetDataQuery } from "../../../../../../../../hooks";
import { COLUMN_NAME, PulseType } from "../../../../../../../../constants/PulseOtelSemcov";
import dayjs from "dayjs";
import {
  UseGetNetworkIssuesParams,
  UseGetNetworkIssuesReturn,
  NetworkIssuesData,
  NetworkIssueData,
} from "./useGetNetworkIssues.interface";
import { FILTER_MAPPING } from "../../../../../../../../hooks/hooks.interface";

export const useGetNetworkIssues = ({
  interactionName,
  startTime,
  endTime,
  enabled = true,
  dashboardFilters,
}: UseGetNetworkIssuesParams): UseGetNetworkIssuesReturn => {

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
        { function: "NET_0" as const, alias: "conn_error" },
        { function: "NET_5XX" as const, alias: "5xx" },
        { function: "NET_4XX" as const, alias: "4xx" },
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

  // Fetch network issues data
  const {
    data,
    isLoading,
    error,
  } = useGetDataQuery({
    requestBody,
    enabled: enabled && !!startTime && !!endTime && !!interactionName,
  });

  // Transform network issues data
  const networkIssuesData = useMemo(() => {
    const responseData = data?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return {
        connectionTimeoutErrorsByNetwork: [] as NetworkIssueData[],
        error5xxByNetwork: [] as NetworkIssueData[],
        error4xxByNetwork: [] as NetworkIssueData[],
      };
    }

    const connErrorIndex = responseData.fields.indexOf("conn_error");
    const error5xxIndex = responseData.fields.indexOf("5xx");
    const error4xxIndex = responseData.fields.indexOf("4xx");
    const networkProviderIndex =
      responseData.fields.indexOf("network_provider");

    const connectionTimeoutErrorsByNetwork = responseData.rows
      .map((row) => ({
        networkProvider: String(row[networkProviderIndex] || ""),
        errors: parseFloat(String(row[connErrorIndex])) || 0,
      }))
      .sort((a, b) => a.errors - b.errors);

    const error5xxByNetwork = responseData.rows
      .map((row) => ({
        networkProvider: String(row[networkProviderIndex] || ""),
        errors: parseFloat(String(row[error5xxIndex])) || 0,
      }))
      .sort((a, b) => a.errors - b.errors);

    const error4xxByNetwork = responseData.rows
      .map((row) => ({
        networkProvider: String(row[networkProviderIndex] || ""),
        errors: parseFloat(String(row[error4xxIndex])) || 0,
      }))
      .sort((a, b) => a.errors - b.errors);

    return {
      connectionTimeoutErrorsByNetwork,
      error5xxByNetwork,
      error4xxByNetwork,
    } as NetworkIssuesData;
  }, [data]);

  const isError = !!error || !!data?.error;

  return {
    networkIssuesData,
    isLoading,
    isError,
    error: error || null,
  };
};

