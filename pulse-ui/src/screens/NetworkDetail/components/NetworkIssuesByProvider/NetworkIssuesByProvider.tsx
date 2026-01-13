import React, { useMemo } from "react";
import { useGetDataQuery } from "../../../../hooks/useGetDataQuery";
import TopIssuesCharts, {
  SectionConfig,
} from "../../../CriticalInteractionDetails/components/InteractionDetailsMainContent/components/Analysis/components/TopIssuesCharts";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState";
import { Box, Text, SimpleGrid } from "@mantine/core";
import { ChartSkeleton, SkeletonLoader } from "../../../../components/Skeletons";
import classes from "./NetworkIssuesByProvider.module.css";
import { IconMoodHappy, IconWifi } from "@tabler/icons-react";

interface NetworkIssuesByProviderProps {
  url: string;
  startTime: string;
  endTime: string;
  shouldFetch: boolean;
  showHeader?: boolean;
  additionalFilters?: Array<{
    field: string;
    operator: "LIKE" | "EQ";
    value: string[];
  }>;
}

export const NetworkIssuesByProvider: React.FC<NetworkIssuesByProviderProps> = ({
  url,
  startTime,
  endTime,
  shouldFetch,
  showHeader = true,
  additionalFilters = [],
}) => {
  // Query for connection errors (network.0)
  const {
    data: connErrorData,
    isLoading: isLoadingConn,
    error: connError,
  } = useGetDataQuery({
    requestBody: {
      dataType: "TRACES",
      timeRange: { start: startTime, end: endTime },
      select: [
        { function: "CUSTOM" as const, param: { expression: "count()" }, alias: "conn_error" },
        { function: "CUSTOM" as const, param: { expression: "ResourceAttributes['network.provider']" }, alias: "network_provider" },
      ],
      groupBy: ["network_provider"],
      filters: [
        { field: "PulseType", operator: "EQ" as const, value: ["network.0"] },
        { field: "SpanAttributes['http.url']", operator: "EQ" as const, value: [url] },
        ...additionalFilters,
      ],
      orderBy: [{ field: "conn_error", direction: "ASC" as const }],
      limit: 10,
    },
    enabled: shouldFetch && !!url && !!startTime && !!endTime,
  });

  // Query for 4xx errors grouped by network provider
  const {
    data: error4xxData,
    isLoading: isLoading4xx,
    error: error4xx,
  } = useGetDataQuery({
    requestBody: {
      dataType: "TRACES",
      timeRange: { start: startTime, end: endTime },
      select: [
        { function: "CUSTOM" as const, param: { expression: "count()" }, alias: "4xx" },
        { function: "CUSTOM" as const, param: { expression: "ResourceAttributes['network.provider']" }, alias: "network_provider" },
      ],
      groupBy: ["network_provider"],
      filters: [
        { field: "PulseType", operator: "LIKE" as const, value: ["network.4%"] },
        { field: "SpanAttributes['http.url']", operator: "EQ" as const, value: [url] },
        ...additionalFilters,
      ],
      orderBy: [{ field: "4xx", direction: "ASC" as const }],
      limit: 10,
    },
    enabled: shouldFetch && !!url && !!startTime && !!endTime,
  });

  // Query for 5xx errors grouped by network provider
  const {
    data: error5xxData,
    isLoading: isLoading5xx,
    error: error5xx,
  } = useGetDataQuery({
    requestBody: {
      dataType: "TRACES",
      timeRange: { start: startTime, end: endTime },
      select: [
        { function: "CUSTOM" as const, param: { expression: "count()" }, alias: "5xx" },
        { function: "CUSTOM" as const, param: { expression: "ResourceAttributes['network.provider']" }, alias: "network_provider" },
      ],
      groupBy: ["network_provider"],
      filters: [
        { field: "PulseType", operator: "LIKE" as const, value: ["network.5%"] },
        { field: "SpanAttributes['http.url']", operator: "EQ" as const, value: [url] },
        ...additionalFilters,
      ],
      orderBy: [{ field: "5xx", direction: "ASC" as const }],
      limit: 10,
    },
    enabled: shouldFetch && !!url && !!startTime && !!endTime,
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

  const sections = useMemo((): SectionConfig[] => {
    if (
      !connectionTimeoutErrorsByNetwork.length &&
      !error5xxByNetwork.length &&
      !error4xxByNetwork.length
    ) {
      return [];
    }

    return [
      {
        title: "Network Issues by Provider",
        description: "Network errors and connection issues grouped by network provider",
        charts: [
          {
            title: "Connection & Timeout Errors",
            description: "Network providers with the highest connection and timeout issues",
            data: connectionTimeoutErrorsByNetwork,
            yAxisDataKey: "networkProvider",
            valueKey: "errors",
            seriesName: "Connection & Timeout Errors",
          },
          {
            title: "5xx Errors",
            description: "Server-side errors by network provider",
            data: error5xxByNetwork,
            yAxisDataKey: "networkProvider",
            valueKey: "errors",
            seriesName: "5xx Errors",
          },
          {
            title: "4xx Errors",
            description: "Client-side errors by network provider",
            data: error4xxByNetwork,
            yAxisDataKey: "networkProvider",
            valueKey: "errors",
            seriesName: "4xx Errors",
          },
        ],
      },
    ];
  }, [connectionTimeoutErrorsByNetwork, error5xxByNetwork, error4xxByNetwork]);

  const isLoading = isLoadingConn || isLoading4xx || isLoading5xx;
  const hasError = connError || error4xx || error5xx;

  if (hasError) {
    return (
      <Box className={classes.container}>
        {showHeader && (
          <Box className={classes.header}>
            <Box className={classes.headerIcon}>
              <IconWifi size={18} />
            </Box>
            <Box className={classes.headerContent}>
              <Text className={classes.title}>Network Issues by Provider</Text>
              <Text className={classes.description}>
                Connection errors grouped by network provider
              </Text>
            </Box>
          </Box>
        )}
      <ErrorAndEmptyState message="Failed to load network issues. Please try again." />
      </Box>
    );
  }

  if (isLoading) {
    return (
      <Box className={classes.container}>
        {showHeader && (
          <Box className={classes.header}>
            <SkeletonLoader height={36} width={36} radius="md" />
            <Box className={classes.headerContent}>
              <SkeletonLoader height={16} width={180} radius="sm" />
              <SkeletonLoader height={12} width={250} radius="xs" />
            </Box>
          </Box>
        )}
        <SimpleGrid cols={{ base: 1, lg: 3 }} spacing="md">
          <ChartSkeleton height={180} />
          <ChartSkeleton height={180} />
          <ChartSkeleton height={180} />
        </SimpleGrid>
      </Box>
    );
  }

  if (sections.length === 0) {
    return (
      <Box className={classes.container}>
        {showHeader && (
          <Box className={classes.header}>
            <Box className={classes.headerIcon}>
              <IconWifi size={18} />
            </Box>
            <Box className={classes.headerContent}>
              <Text className={classes.title}>Network Issues by Provider</Text>
              <Text className={classes.description}>
                Connection errors grouped by network provider
              </Text>
            </Box>
          </Box>
        )}
        <Box className={classes.emptyState}>
          <IconMoodHappy size={32} className={classes.emptyIcon} />
          <Text className={classes.emptyMessage}>
            No network issues found for this API
          </Text>
        </Box>
      </Box>
    );
  }

  return (
    <Box className={classes.container}>
      {showHeader && (
        <Box className={classes.header}>
          <Box className={classes.headerIcon}>
            <IconWifi size={18} />
          </Box>
          <Box className={classes.headerContent}>
            <Text className={classes.title}>Network Issues by Provider</Text>
            <Text className={classes.description}>
              Connection errors grouped by network provider
          </Text>
          </Box>
        </Box>
      )}
      <TopIssuesCharts sections={sections} />
    </Box>
  );
};
