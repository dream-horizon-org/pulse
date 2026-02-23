import React, { useMemo } from "react";
import { Box, Text } from "@mantine/core";
import { IconChartLine, IconTrendingUp } from "@tabler/icons-react";
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
import { StatusCodeTimeSeriesProps } from "./StatusCodeTimeSeries.interface";
import classes from "./StatusCodeTimeSeries.module.css";

// Color palette for status code categories
const STATUS_CODE_COLORS: Record<string, string> = {
  "2xx": "#22c55e", // Green - Success
  "3xx": "#3b82f6", // Blue - Redirect
  "4xx": "#f59e0b", // Orange - Client Error
  "5xx": "#ef4444", // Red - Server Error
  "0xx": "#8b5cf6", // Purple - Connection/Timeout Error
};

const STATUS_CODE_LABELS: Record<string, string> = {
  "2xx": "Success (2xx)",
  "3xx": "Redirect (3xx)",
  "4xx": "Client Error (4xx)",
  "5xx": "Server Error (5xx)",
  "0xx": "Connection Error",
};

export const StatusCodeTimeSeries: React.FC<StatusCodeTimeSeriesProps> = ({
  url,
  startTime,
  endTime,
  additionalFilters = [],
  queryResult,
}) => {
  const bucketSize = useMemo(
    () => getTimeBucketSize(startTime, endTime),
    [startTime, endTime]
  );

  // Query for time series data grouped by status code category
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
            expression: "SpanAttributes['http.status_code']",
          },
          alias: "status_code",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: "count()",
          },
          alias: "request_count",
        },
      ],
      groupBy: ["t1", "status_code"],
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
  const { seriesData, timePoints } = useMemo(() => {
    if (!resolvedData?.data?.rows || resolvedData.data.rows.length === 0) {
      return { seriesData: {}, timePoints: [] };
    }

    const fields = resolvedData.data.fields;
    const timeIndex = fields.indexOf("t1");
    const statusCodeIndex = fields.indexOf("status_code");
    const countIndex = fields.indexOf("request_count");

    // Group data by timestamp and category
    const timeMap: Record<string, Record<string, number>> = {};

    resolvedData.data.rows.forEach((row: any) => {
      const timestamp = row[timeIndex];
      const statusCode = String(row[statusCodeIndex] || "0");
      const count = parseFloat(String(row[countIndex])) || 0;

      // Determine category
      const statusNum = parseInt(statusCode, 10);
      let category: string;
      if (isNaN(statusNum) || statusNum === 0) {
        category = "0xx";
      } else if (statusNum >= 200 && statusNum < 300) {
        category = "2xx";
      } else if (statusNum >= 300 && statusNum < 400) {
        category = "3xx";
      } else if (statusNum >= 400 && statusNum < 500) {
        category = "4xx";
      } else if (statusNum >= 500 && statusNum < 600) {
        category = "5xx";
      } else {
        category = "0xx";
      }

      if (!timeMap[timestamp]) {
        timeMap[timestamp] = { "2xx": 0, "3xx": 0, "4xx": 0, "5xx": 0, "0xx": 0 };
      }
      timeMap[timestamp][category] += count;
    });

    // Sort timestamps and build series
    const sortedTimes = Object.keys(timeMap).sort(
      (a, b) => new Date(a).getTime() - new Date(b).getTime()
    );

    const series: Record<string, number[]> = {
      "2xx": [],
      "3xx": [],
      "4xx": [],
      "5xx": [],
      "0xx": [],
    };

    sortedTimes.forEach((time) => {
      const data = timeMap[time];
      const total =
        (data["2xx"] || 0) +
        (data["3xx"] || 0) +
        (data["4xx"] || 0) +
        (data["5xx"] || 0) +
        (data["0xx"] || 0);
      series["2xx"].push(total > 0 ? (data["2xx"] || 0) / total * 100 : 0);
      series["3xx"].push(total > 0 ? (data["3xx"] || 0) / total * 100 : 0);
      series["4xx"].push(total > 0 ? (data["4xx"] || 0) / total * 100 : 0);
      series["5xx"].push(total > 0 ? (data["5xx"] || 0) / total * 100 : 0);
      series["0xx"].push(total > 0 ? (data["0xx"] || 0) / total * 100 : 0);
    });

    return { seriesData: series, timePoints: sortedTimes };
  }, [resolvedData]);

  // Check if there's any data
  const hasData = timePoints.length > 0;

  // Build chart series - only include categories with data
  const chartSeries = useMemo(() => {
    const categories = ["2xx", "3xx", "4xx", "5xx", "0xx"];
    return categories
      .filter((cat) => seriesData[cat]?.some((v) => v > 0))
      .map((category) => ({
        name: STATUS_CODE_LABELS[category],
        type: "line" as const,
        emphasis: { focus: "series" as const },
        color: STATUS_CODE_COLORS[category],
        data: seriesData[category] || [],
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 2 },
      }));
  }, [seriesData]);

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
            <IconTrendingUp size={18} />
          </Box>
          <Box className={classes.headerContent}>
            <Text className={classes.title}>Status Code Trend</Text>
            <Text className={classes.description}>
              Response status over time
            </Text>
          </Box>
        </Box>
        <ErrorAndEmptyState message="Failed to load status code trend. Please try again." />
      </Box>
    );
  }

  if (resolvedLoading) {
    return (
      <Box className={classes.container}>
        <Box className={classes.header}>
          <Box className={classes.headerIcon}>
            <IconTrendingUp size={18} />
          </Box>
          <Box className={classes.headerContent}>
            <Text className={classes.title}>Status Code Trend</Text>
            <Text className={classes.description}>
              Response status over time
            </Text>
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
            <IconTrendingUp size={18} />
          </Box>
          <Box className={classes.headerContent}>
            <Text className={classes.title}>Status Code Trend</Text>
            <Text className={classes.description}>
              Response status over time
            </Text>
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
          <IconTrendingUp size={18} />
        </Box>
        <Box className={classes.headerContent}>
          <Text className={classes.title}>Status Code Trend</Text>
          <Text className={classes.description}>
            Spot spikes and patterns over time
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
                valueFormatter: (value: number) => `${value.toFixed(1)}%`,
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
                fontSize: 10,
              },
            },
            yAxis: {
              type: "value",
              name: "Percent",
              nameTextStyle: { fontSize: 11 },
              min: 0,
              max: 100,
              interval: 20,
              axisLabel: {
                fontSize: 10,
                formatter: (value: number) => `${value}%`,
              },
            },
            series: chartSeries,
          }}
        />
      </Box>
    </Box>
  );
};

