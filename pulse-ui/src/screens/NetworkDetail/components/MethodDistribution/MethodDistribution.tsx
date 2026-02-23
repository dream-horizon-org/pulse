import React, { useMemo } from "react";
import { Box, Text } from "@mantine/core";
import { IconChartDonut, IconApi } from "@tabler/icons-react";
import { useGetDataQuery } from "../../../../hooks/useGetDataQuery";
import {
  PieChart,
  CustomToolTip,
  createTooltipFormatter,
} from "../../../../components/Charts";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState";
import { ChartSkeleton } from "../../../../components/Skeletons";
import {
  MethodDistributionProps,
  MethodData,
} from "./MethodDistribution.interface";
import classes from "./MethodDistribution.module.css";

// Color palette for HTTP methods
const METHOD_COLORS: Record<string, string> = {
  GET: "#3b82f6", // Blue
  POST: "#22c55e", // Green
  PUT: "#f59e0b", // Orange
  PATCH: "#8b5cf6", // Purple
  DELETE: "#ef4444", // Red
  HEAD: "#6366f1", // Indigo
  OPTIONS: "#14b8a6", // Teal
  CONNECT: "#ec4899", // Pink
  TRACE: "#78716c", // Stone
};

const getMethodColor = (method: string): string => {
  return METHOD_COLORS[method.toUpperCase()] || "#6c757d";
};

const OPERATION_TYPE_COLORS: Record<string, string> = {
  QUERY: "#3b82f6",
  MUTATION: "#f59e0b",
  SUBSCRIPTION: "#8b5cf6",
  UNKNOWN: "#6c757d",
};

const getOperationTypeColor = (operationType: string): string => {
  return OPERATION_TYPE_COLORS[operationType.toUpperCase()] || "#6c757d";
};

export const MethodDistribution: React.FC<MethodDistributionProps> = ({
  url,
  startTime,
  endTime,
  additionalFilters = [],
  mode = "http_method",
  queryResult,
}) => {
  const isGraphqlMode = mode === "graphql_operation_type";
  const dimensionLabel = isGraphqlMode
    ? "Operation Type Distribution"
    : "HTTP Method Distribution";
  const dimensionDescription = isGraphqlMode
    ? "Request breakdown by GraphQL operation type"
    : "Request breakdown by HTTP method type";
  const dimensionExpression = isGraphqlMode
    ? "SpanAttributes['graphql.operation.type']"
    : "SpanAttributes['http.method']";
  const dimensionAlias = isGraphqlMode ? "operation_type" : "http_method";

  const shouldFetch = !queryResult;
  // Query for all network requests grouped by HTTP method
  const { data, isLoading, error } = useGetDataQuery({
    requestBody: {
      dataType: "TRACES",
      timeRange: { start: startTime, end: endTime },
      select: [
        {
          function: "CUSTOM" as const,
          param: {
            expression: "count()",
          },
          alias: "request_count",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: dimensionExpression,
          },
          alias: dimensionAlias,
        },
      ],
      groupBy: [dimensionAlias],
      filters: [
        {
          field: "PulseType",
          operator: "LIKE" as const,
          value: ["%network%"],
        },
        {
          field: "SpanAttributes['http.url']",
          operator: "EQ" as const,
          value: [url],
        },
        ...additionalFilters,
      ],
      orderBy: [
        {
          field: "request_count",
          direction: "DESC" as const,
        },
      ],
    },
    enabled: shouldFetch && !!url && !!startTime && !!endTime,
  });
  const resolvedData = queryResult?.data ?? data;
  const resolvedLoading = queryResult?.isLoading ?? isLoading;
  const resolvedError = queryResult?.error ?? error;

  // Transform and prepare method data
  const { methods, totalRequests } = useMemo(() => {
    if (!resolvedData?.data?.rows || resolvedData.data.rows.length === 0) {
      return { methods: [], totalRequests: 0 };
    }

    const fields = resolvedData.data.fields;
    const countIndex = fields.indexOf("request_count");
    const methodIndex = fields.indexOf(dimensionAlias);

    let total = 0;
    const methodTotals = new Map<string, number>();

    resolvedData.data.rows.forEach((row: any) => {
      const methodRaw = String(row[methodIndex] || "UNKNOWN");
      const method = methodRaw.trim().toUpperCase() || "UNKNOWN";
      const count = parseFloat(String(row[countIndex])) || 0;
      total += count;
      methodTotals.set(method, (methodTotals.get(method) || 0) + count);
    });

    const methodsData: MethodData[] = Array.from(methodTotals.entries()).map(
      ([method, count]) => ({
        method,
        count: Math.round(count),
        percentage: 0,
        color: isGraphqlMode ? getOperationTypeColor(method) : getMethodColor(method),
      })
    );

    // Calculate percentages
    const methodsWithPercentages = methodsData
      .map((m) => ({
        ...m,
        percentage: total > 0 ? Math.round((m.count / total) * 1000) / 10 : 0,
      }))
      .sort((a, b) => b.count - a.count);

    return { methods: methodsWithPercentages, totalRequests: Math.round(total) };
  }, [resolvedData, dimensionAlias, isGraphqlMode]);

  // ECharts pie chart options
  const chartOption = useMemo(() => {
    if (methods.length === 0) return {};

    return {
      tooltip: {
        ...CustomToolTip,
        trigger: "item",
        confine: true,
        formatter: createTooltipFormatter({
          valueFormatter: (value: number) => value.toLocaleString(),
        }),
      },
      legend: {
        show: false, // We'll use custom legend
      },
      series: [
        {
          name: dimensionLabel,
          type: "pie",
          radius: ["55%", "85%"],
          center: ["50%", "50%"],
          avoidLabelOverlap: true,
          itemStyle: {
            borderRadius: 4,
            borderColor: "#fff",
            borderWidth: 2,
          },
          label: {
            show: true,
            position: "inside",
            formatter: (params: { percent: number }) =>
              params.percent >= 5 ? `${Math.round(params.percent)}%` : "",
            fontWeight: 600,
            fontSize: 11,
            color: "#fff",
          },
          labelLine: {
            show: false,
          },
          emphasis: {
            itemStyle: {
              shadowBlur: 10,
              shadowOffsetX: 0,
              shadowColor: "rgba(0, 0, 0, 0.2)",
            },
            label: {
              show: true,
              fontWeight: 700,
            },
          },
          data: methods.map((m) => ({
            value: m.count,
            name: m.method,
            itemStyle: { color: m.color },
          })),
        },
      ],
    };
  }, [methods, dimensionLabel]);

  const hasError = resolvedError || resolvedData?.error;
  if (hasError) {
    return (
      <Box className={classes.container}>
        <Box className={classes.header}>
          <Box className={classes.headerIcon}>
            <IconApi size={18} />
          </Box>
          <Box className={classes.headerContent}>
            <Text className={classes.title}>{dimensionLabel}</Text>
            <Text className={classes.description}>{dimensionDescription}</Text>
          </Box>
        </Box>
        <ErrorAndEmptyState message="Failed to load method distribution. Please try again." />
      </Box>
    );
  }

  if (resolvedLoading) {
    return (
      <Box className={classes.container}>
        <Box className={classes.header}>
          <Box className={classes.headerIcon}>
            <IconApi size={18} />
          </Box>
          <Box className={classes.headerContent}>
            <Text className={classes.title}>{dimensionLabel}</Text>
            <Text className={classes.description}>{dimensionDescription}</Text>
          </Box>
        </Box>
        <ChartSkeleton height={220} />
      </Box>
    );
  }

  if (methods.length === 0) {
    return (
      <Box className={classes.container}>
        <Box className={classes.header}>
          <Box className={classes.headerIcon}>
            <IconApi size={18} />
          </Box>
          <Box className={classes.headerContent}>
            <Text className={classes.title}>{dimensionLabel}</Text>
            <Text className={classes.description}>{dimensionDescription}</Text>
          </Box>
        </Box>
        <Box className={classes.emptyState}>
          <IconChartDonut size={32} className={classes.emptyIcon} />
          <Text className={classes.emptyMessage}>
            No request data available for this time range
          </Text>
        </Box>
      </Box>
    );
  }

  return (
    <Box className={classes.container}>
      <Box className={classes.header}>
        <Box className={classes.headerIcon}>
          <IconApi size={18} />
        </Box>
        <Box className={classes.headerContent}>
          <Text className={classes.title}>{dimensionLabel}</Text>
          <Text className={classes.description}>{dimensionDescription}</Text>
        </Box>
      </Box>

      <Box className={classes.chartContainer}>
        {/* Donut Chart */}
        <Box className={classes.donutWrapper}>
          <PieChart height={220} option={chartOption} withLegend={false} />
          <Box className={classes.centerLabel}>
            <Text className={classes.totalCount}>
              {totalRequests.toLocaleString()}
            </Text>
            <Text className={classes.totalLabel}>Total</Text>
          </Box>
        </Box>

        {/* Custom Legend with counts and percentages */}
        <Box className={classes.legendContainer}>
          {methods.map((method) => (
            <Box key={method.method} className={classes.legendItem}>
              <Box className={classes.legendLeft}>
                <Box
                  className={classes.methodBadge}
                  style={{
                    backgroundColor: `${method.color}20`,
                    color: method.color,
                    border: `1px solid ${method.color}40`,
                  }}
                >
                  {method.method}
                </Box>
              </Box>
              <Box className={classes.legendRight}>
                <Text
                  className={classes.legendCount}
                  style={{ color: method.color }}
                >
                  {method.count.toLocaleString()}
                </Text>
                <Text className={classes.legendPercentage}>
                  {method.percentage}%
                </Text>
              </Box>
            </Box>
          ))}
        </Box>
      </Box>
    </Box>
  );
};

