import React, { useMemo } from "react";
import { Box, Text } from "@mantine/core";
import { IconChartLine, IconActivity } from "@tabler/icons-react";
import dayjs from "dayjs";
import { useGetDataQuery } from "../../../../hooks/useGetDataQuery";
import {
  AreaChart,
  CustomToolTip,
  createTooltipFormatter,
} from "../../../../components/Charts";
import { ChartSkeleton } from "../../../../components/Skeletons";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState";
import { getTimeBucketSize } from "../../../../utils/TimeBucketUtil";
import { MethodTimeSeriesProps } from "./MethodTimeSeries.interface";
import classes from "./MethodTimeSeries.module.css";

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
  UNKNOWN: "#6c757d", // Gray
};

const OPERATION_TYPE_COLORS: Record<string, string> = {
  QUERY: "#3b82f6",
  MUTATION: "#f59e0b",
  SUBSCRIPTION: "#8b5cf6",
  UNKNOWN: "#6c757d",
};

export const MethodTimeSeries: React.FC<MethodTimeSeriesProps> = ({
  url,
  startTime,
  endTime,
  additionalFilters = [],
  mode = "http_method",
  queryResult,
}) => {
  const isGraphqlMode = mode === "graphql_operation_type";
  const dimensionLabel = isGraphqlMode ? "Operation Type Trend" : "HTTP Method Trend";
  const dimensionDescription = isGraphqlMode
    ? "Request types over time"
    : "Request methods over time";
  const dimensionExpression = isGraphqlMode
    ? "SpanAttributes['graphql.operation.type']"
    : "SpanAttributes['http.method']";
  const dimensionAlias = isGraphqlMode ? "operation_type" : "http_method";

  const bucketSize = useMemo(
    () => getTimeBucketSize(startTime, endTime),
    [startTime, endTime]
  );

  // Query for time series data grouped by HTTP method
  const shouldFetch = !queryResult;
  const { data, isLoading, error } = useGetDataQuery({
    requestBody: {
      dataType: "TRACES",
      timeRange: { start: startTime, end: endTime },
      select: [
        {
          function: "TIME_BUCKET" as const,
          param: { bucket: bucketSize, field: "Timestamp" },
          alias: "t1",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: dimensionExpression,
          },
          alias: dimensionAlias,
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: "count()",
          },
          alias: "request_count",
        },
      ],
      groupBy: ["t1", dimensionAlias],
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
          field: "t1",
          direction: "ASC" as const,
        },
      ],
    },
    enabled: shouldFetch && !!url && !!startTime && !!endTime,
  });
  const resolvedData = queryResult?.data ?? data;
  const resolvedLoading = queryResult?.isLoading ?? isLoading;
  const resolvedError = queryResult?.error ?? error;

  // Transform data into time series format
  const { seriesData, timePoints, methods } = useMemo(() => {
    if (!resolvedData?.data?.rows || resolvedData.data.rows.length === 0) {
      return { seriesData: {}, timePoints: [], methods: [] };
    }

    const fields = resolvedData.data.fields;
    const timeIndex = fields.indexOf("t1");
    const methodIndex = fields.indexOf(dimensionAlias);
    const countIndex = fields.indexOf("request_count");

    // Collect all unique methods and group data by timestamp
    const methodSet = new Set<string>();
    const timeMap: Record<string, Record<string, number>> = {};

    resolvedData.data.rows.forEach((row: any) => {
      const timestamp = row[timeIndex];
      const method = String(row[methodIndex] || "UNKNOWN").toUpperCase();
      const count = parseFloat(String(row[countIndex])) || 0;

      methodSet.add(method);

      if (!timeMap[timestamp]) {
        timeMap[timestamp] = {};
      }
      timeMap[timestamp][method] = (timeMap[timestamp][method] || 0) + count;
    });

    const allMethods = Array.from(methodSet).sort();

    // Sort timestamps and build series
    const sortedTimes = Object.keys(timeMap).sort(
      (a, b) => new Date(a).getTime() - new Date(b).getTime()
    );

    const series: Record<string, number[]> = {};
    allMethods.forEach((method) => {
      series[method] = [];
    });

    sortedTimes.forEach((time) => {
      const data = timeMap[time];
      const total = allMethods.reduce(
        (sum, method) => sum + (data[method] || 0),
        0
      );
      allMethods.forEach((method) => {
        const value = data[method] || 0;
        const normalized = isGraphqlMode
          ? total > 0
            ? (value / total) * 100
            : 0
          : value;
        series[method].push(normalized);
      });
    });

    return { seriesData: series, timePoints: sortedTimes, methods: allMethods };
  }, [resolvedData, dimensionAlias, isGraphqlMode]);

  // Check if there's any data
  const hasData = timePoints.length > 0;

  // Build chart series
  const chartSeries = useMemo(() => {
    return methods
      .filter((method) => seriesData[method]?.some((v) => v > 0))
      .map((method) => ({
        name: method,
        type: "line" as const,
        emphasis: { focus: "series" as const },
        color: isGraphqlMode
          ? OPERATION_TYPE_COLORS[method] || "#6c757d"
          : METHOD_COLORS[method] || "#6c757d",
        data: seriesData[method] || [],
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 2 },
        ...(isGraphqlMode
          ? {}
          : {
              stack: "total",
              areaStyle: { opacity: 0.4 },
            }),
      }));
  }, [methods, seriesData, isGraphqlMode]);

  // Format time for display
  const formatTime = (timestamp: string) => {
    const date = dayjs(timestamp);
    if (bucketSize.includes("d")) {
      return date.format("MMM DD");
    } else if (bucketSize.includes("h")) {
      return date.format("MMM DD HH:mm");
    }
    return date.format("HH:mm");
  };

  if (resolvedError || resolvedData?.error) {
    return (
      <Box className={classes.container}>
        <Box className={classes.header}>
          <Box className={classes.headerIcon}>
            <IconActivity size={18} />
          </Box>
          <Box className={classes.headerContent}>
            <Text className={classes.title}>{dimensionLabel}</Text>
            <Text className={classes.description}>{dimensionDescription}</Text>
          </Box>
        </Box>
        <ErrorAndEmptyState message="Failed to load method trend. Please try again." />
      </Box>
    );
  }

  if (resolvedLoading) {
    return (
      <Box className={classes.container}>
        <Box className={classes.header}>
          <Box className={classes.headerIcon}>
            <IconActivity size={18} />
          </Box>
          <Box className={classes.headerContent}>
            <Text className={classes.title}>{dimensionLabel}</Text>
            <Text className={classes.description}>{dimensionDescription}</Text>
          </Box>
        </Box>
        <ChartSkeleton height={280} />
      </Box>
    );
  }

  if (!hasData) {
    return (
      <Box className={classes.container}>
        <Box className={classes.header}>
          <Box className={classes.headerIcon}>
            <IconActivity size={18} />
          </Box>
          <Box className={classes.headerContent}>
            <Text className={classes.title}>{dimensionLabel}</Text>
            <Text className={classes.description}>{dimensionDescription}</Text>
          </Box>
        </Box>
        <Box className={classes.emptyState}>
          <IconChartLine size={32} className={classes.emptyIcon} />
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
          <IconActivity size={18} />
        </Box>
        <Box className={classes.headerContent}>
          <Text className={classes.title}>{dimensionLabel}</Text>
          <Text className={classes.description}>
            {isGraphqlMode ? "Spot operation patterns over time" : "Spot traffic patterns over time"}
          </Text>
        </Box>
      </Box>

      <Box className={classes.chartContainer}>
        <AreaChart
          height={280}
          withLegend={true}
          option={{
            grid: {
              left: 50,
              right: 20,
              top: 20,
              bottom: 60,
            },
            tooltip: {
              ...CustomToolTip,
              trigger: "axis",
              confine: true,
              formatter: createTooltipFormatter({
                valueFormatter: (value: number) =>
                  isGraphqlMode ? `${value.toFixed(1)}%` : value.toLocaleString(),
                customHeaderFormatter: (axisValue: any) => axisValue || "",
              }),
            },
            legend: {
              bottom: 0,
              textStyle: { fontSize: 11 },
            },
            xAxis: {
              type: "category",
              data: timePoints.map(formatTime),
              axisTick: {
                alignWithLabel: true,
              },
              axisLabel: {
                fontSize: 10
              },
            },
            yAxis: {
              type: "value",
              name: isGraphqlMode ? "Percent" : "Requests",
              nameTextStyle: { fontSize: 11 },
              min: isGraphqlMode ? 0 : undefined,
              max: isGraphqlMode ? 100 : undefined,
              interval: isGraphqlMode ? 20 : undefined,
              axisLabel: {
                fontSize: 10,
                formatter: (value: number) => {
                  if (isGraphqlMode) return `${value}%`;
                  if (value >= 1000) return `${(value / 1000).toFixed(1)}k`;
                  return value.toString();
                },
              },
            },
            series: chartSeries,
          }}
        />
      </Box>
    </Box>
  );
};

