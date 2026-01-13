import React, { useMemo } from "react";
import { Box, Text } from "@mantine/core";
import { IconChartLine, IconTrendingUp } from "@tabler/icons-react";
import dayjs from "dayjs";
import { useGetDataQuery } from "../../../../hooks/useGetDataQuery";
import { AreaChart } from "../../../../components/Charts";
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
}) => {
  const bucketSize = useMemo(
    () => getTimeBucketSize(startTime, endTime),
    [startTime, endTime]
  );

  // Query for time series data grouped by status code category
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
    enabled: !!url && !!startTime && !!endTime,
  });

  // Transform data into time series format
  const { seriesData, timePoints } = useMemo(() => {
    if (!data?.data?.rows || data.data.rows.length === 0) {
      return { seriesData: {}, timePoints: [] };
    }

    const fields = data.data.fields;
    const timeIndex = fields.indexOf("t1");
    const statusCodeIndex = fields.indexOf("status_code");
    const countIndex = fields.indexOf("request_count");

    // Group data by timestamp and category
    const timeMap: Record<string, Record<string, number>> = {};

    data.data.rows.forEach((row) => {
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
      series["2xx"].push(data["2xx"] || 0);
      series["3xx"].push(data["3xx"] || 0);
      series["4xx"].push(data["4xx"] || 0);
      series["5xx"].push(data["5xx"] || 0);
      series["0xx"].push(data["0xx"] || 0);
    });

    return { seriesData: series, timePoints: sortedTimes };
  }, [data]);

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
        stack: "total",
        areaStyle: { opacity: 0.4 },
        emphasis: { focus: "series" as const },
        color: STATUS_CODE_COLORS[category],
        data: seriesData[category] || [],
        smooth: true,
        showSymbol: false,
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

  if (error || data?.error) {
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

  if (isLoading) {
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
                fontSize: 10,
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

