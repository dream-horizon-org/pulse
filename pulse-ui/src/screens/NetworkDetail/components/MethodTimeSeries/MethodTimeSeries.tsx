import React, { useMemo } from "react";
import { Box, Text } from "@mantine/core";
import { IconChartLine, IconActivity } from "@tabler/icons-react";
import dayjs from "dayjs";
import { useGetDataQuery } from "../../../../hooks/useGetDataQuery";
import { AreaChart } from "../../../../components/Charts";
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

export const MethodTimeSeries: React.FC<MethodTimeSeriesProps> = ({
  url,
  startTime,
  endTime,
  additionalFilters = [],
}) => {
  const bucketSize = useMemo(
    () => getTimeBucketSize(startTime, endTime),
    [startTime, endTime]
  );

  // Query for time series data grouped by HTTP method
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
            expression: "SpanAttributes['http.method']",
          },
          alias: "http_method",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: "count()",
          },
          alias: "request_count",
        },
      ],
      groupBy: ["t1", "http_method"],
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
    enabled: !!url && !!startTime && !!endTime,
  });

  // Transform data into time series format
  const { seriesData, timePoints, methods } = useMemo(() => {
    if (!data?.data?.rows || data.data.rows.length === 0) {
      return { seriesData: {}, timePoints: [], methods: [] };
    }

    const fields = data.data.fields;
    const timeIndex = fields.indexOf("t1");
    const methodIndex = fields.indexOf("http_method");
    const countIndex = fields.indexOf("request_count");

    // Collect all unique methods and group data by timestamp
    const methodSet = new Set<string>();
    const timeMap: Record<string, Record<string, number>> = {};

    data.data.rows.forEach((row) => {
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
      allMethods.forEach((method) => {
        series[method].push(data[method] || 0);
      });
    });

    return { seriesData: series, timePoints: sortedTimes, methods: allMethods };
  }, [data]);

  // Check if there's any data
  const hasData = timePoints.length > 0;

  // Build chart series
  const chartSeries = useMemo(() => {
    return methods
      .filter((method) => seriesData[method]?.some((v) => v > 0))
      .map((method) => ({
        name: method,
        type: "line" as const,
        stack: "total",
        areaStyle: { opacity: 0.4 },
        emphasis: { focus: "series" as const },
        color: METHOD_COLORS[method] || "#6c757d",
        data: seriesData[method] || [],
        smooth: true,
        showSymbol: false,
      }));
  }, [methods, seriesData]);

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

  if (error || data?.error) {
    return (
      <Box className={classes.container}>
        <Box className={classes.header}>
          <Box className={classes.headerIcon}>
            <IconActivity size={18} />
          </Box>
          <Box className={classes.headerContent}>
            <Text className={classes.title}>HTTP Method Trend</Text>
            <Text className={classes.description}>
              Request methods over time
            </Text>
          </Box>
        </Box>
        <ErrorAndEmptyState message="Failed to load method trend. Please try again." />
      </Box>
    );
  }

  if (isLoading) {
    return (
      <Box className={classes.container}>
        <Box className={classes.header}>
          <Box className={classes.headerIcon}>
            <IconActivity size={18} />
          </Box>
          <Box className={classes.headerContent}>
            <Text className={classes.title}>HTTP Method Trend</Text>
            <Text className={classes.description}>
              Request methods over time
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
            <IconActivity size={18} />
          </Box>
          <Box className={classes.headerContent}>
            <Text className={classes.title}>HTTP Method Trend</Text>
            <Text className={classes.description}>
              Request methods over time
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
          <IconActivity size={18} />
        </Box>
        <Box className={classes.headerContent}>
          <Text className={classes.title}>HTTP Method Trend</Text>
          <Text className={classes.description}>
            Spot traffic patterns over time
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
              trigger: "axis",
              confine: true,
              formatter: (params: any) => {
                if (!params || !Array.isArray(params) || params.length === 0) return "";
                const dataIndex = params[0]?.dataIndex;
                const timestamp = timePoints[dataIndex];
                const header = timestamp
                  ? dayjs(timestamp).format("MMM DD, YYYY HH:mm")
                  : params[0]?.axisValue || "";
                
                let content = `<div style="font-weight:600;margin-bottom:8px">${header}</div>`;
                params.forEach((item: any) => {
                  if (item.value > 0) {
                    content += `
                      <div style="display:flex;align-items:center;gap:8px;margin:4px 0">
                        <span style="display:inline-block;width:10px;height:10px;border-radius:2px;background:${item.color}"></span>
                        <span style="flex:1">${item.seriesName}</span>
                        <span style="font-weight:600">${item.value.toLocaleString()}</span>
                      </div>
                    `;
                  }
                });
                return content;
              },
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
              name: "Requests",
              nameTextStyle: { fontSize: 11 },
              axisLabel: {
                fontSize: 10,
                formatter: (value: number) => {
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

