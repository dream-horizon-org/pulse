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
  const normalizeProvider = (value: unknown) => {
    const provider = String(value || "").trim();
    return provider.length > 0 ? provider : "Unknown";
  };

  const { data, isLoading, error } = useGetDataQuery({
    requestBody: {
      dataType: "TRACES",
      timeRange: { start: startTime, end: endTime },
      select: [
        { function: "CUSTOM" as const, param: { expression: "count()" }, alias: "error_count" },
        { function: "CUSTOM" as const, param: { expression: "NetworkProvider" }, alias: "network_provider" },
        { function: "CUSTOM" as const, param: { expression: "SpanAttributes['http.status_code']" }, alias: "status_code" },
        { function: "CUSTOM" as const, param: { expression: "PulseType" }, alias: "pulse_type" },
      ],
      groupBy: ["network_provider", "status_code", "pulse_type"],
      filters: [
        {
          field: "PulseType",
          operator: "LIKE" as const,
          value: ["%network%"],
        },
        { field: "SpanAttributes['http.url']", operator: "EQ" as const, value: [url] },
        ...additionalFilters,
      ],
      orderBy: [{ field: "error_count", direction: "ASC" as const }],
      limit: 100,
    },
    enabled: shouldFetch && !!url && !!startTime && !!endTime,
  });

  const { connectionTimeoutErrorsByNetwork, error4xxByNetwork, error5xxByNetwork } = useMemo(() => {
    if (!data?.data?.rows || data.data.rows.length === 0) {
      return {
        connectionTimeoutErrorsByNetwork: [],
        error4xxByNetwork: [],
        error5xxByNetwork: [],
      };
    }

    const fields = data.data.fields;
    const countIndex = fields.indexOf("error_count");
    const providerIndex = fields.indexOf("network_provider");
    const statusCodeIndex = fields.indexOf("status_code");
    const pulseTypeIndex = fields.indexOf("pulse_type");

    const connMap = new Map<string, number>();
    const error4xxMap = new Map<string, number>();
    const error5xxMap = new Map<string, number>();

    data.data.rows.forEach((row: any) => {
      const provider = normalizeProvider(row[providerIndex]);
      const count = parseFloat(String(row[countIndex])) || 0;
      const statusCode = String(row[statusCodeIndex] || "0");
      const pulseType = String(row[pulseTypeIndex] || "");
      const statusNum = parseInt(statusCode, 10);

      const isConnectionError =
        pulseType.startsWith("network.0") || isNaN(statusNum) || statusNum === 0;
      if (isConnectionError) {
        connMap.set(provider, (connMap.get(provider) || 0) + count);
        return;
      }
      if (statusNum >= 400 && statusNum < 500) {
        error4xxMap.set(provider, (error4xxMap.get(provider) || 0) + count);
        return;
      }
      if (statusNum >= 500 && statusNum < 600) {
        error5xxMap.set(provider, (error5xxMap.get(provider) || 0) + count);
      }
    });

    const toArray = (map: Map<string, number>) =>
      Array.from(map.entries())
        .map(([networkProvider, errors]) => ({ networkProvider, errors: Math.round(errors) }))
        .filter((item) => item.errors > 0);

    return {
      connectionTimeoutErrorsByNetwork: toArray(connMap),
      error4xxByNetwork: toArray(error4xxMap),
      error5xxByNetwork: toArray(error5xxMap),
    };
  }, [data]);

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

  const hasError = error;

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
