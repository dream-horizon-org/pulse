import React, { useMemo } from "react";
import { Box, Text } from "@mantine/core";
import { IconChartLine, IconClock } from "@tabler/icons-react";
import dayjs from "dayjs";
import { useGetDataQuery } from "../../../../hooks/useGetDataQuery";
import { LineChart, CustomToolTip, createTooltipFormatter } from "../../../../components/Charts";
import { ChartSkeleton } from "../../../../components/Skeletons";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState";
import { getTimeBucketSize } from "../../../../utils/TimeBucketUtil";
import { LatencyTimeSeriesProps } from "./LatencyTimeSeries.interface";
import classes from "./LatencyTimeSeries.module.css";

const SERIES_COLORS: Record<string, string> = {
  avg: "#0ec9c2",
  p50: "#22c55e",
  p95: "#f59e0b",
  p99: "#ef4444",
};

const formatLatency = (value: number) => {
  if (!Number.isFinite(value)) return "—";
  if (value >= 1000) {
    return `${(value / 1000).toFixed(2)}s`;
  }
  return `${value.toFixed(0)}ms`;
};

export const LatencyTimeSeries: React.FC<LatencyTimeSeriesProps> = ({
  url,
  startTime,
  endTime,
  additionalFilters = [],
}) => {
  const bucketSize = useMemo(
    () => getTimeBucketSize(startTime, endTime),
    [startTime, endTime]
  );

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
          param: { expression: "avg(Duration)" },
          alias: "avg_duration",
        },
        { function: "DURATION_P50" as const, alias: "p50" },
        { function: "DURATION_P95" as const, alias: "p95" },
        { function: "DURATION_P99" as const, alias: "p99" },
      ],
      groupBy: ["t1"],
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

  const { timePoints, seriesData } = useMemo(() => {
    if (!data?.data?.rows || data.data.rows.length === 0) {
      return { timePoints: [], seriesData: { avg: [], p50: [], p95: [], p99: [] } };
    }

    const fields = data.data.fields;
    const timeIndex = fields.indexOf("t1");
    const avgIndex = fields.indexOf("avg_duration");
    const p50Index = fields.indexOf("p50");
    const p95Index = fields.indexOf("p95");
    const p99Index = fields.indexOf("p99");

    const timePoints: string[] = [];
    const series = {
      avg: [] as number[],
      p50: [] as number[],
      p95: [] as number[],
      p99: [] as number[],
    };

    data.data.rows.forEach((row) => {
      timePoints.push(row[timeIndex]);

      const avgNs = parseFloat(String(row[avgIndex])) || 0;
      const avgMs = avgNs / 1_000_000;
      series.avg.push(Math.round(avgMs));
      series.p50.push(Math.round(parseFloat(String(row[p50Index])) || 0));
      series.p95.push(Math.round(parseFloat(String(row[p95Index])) || 0));
      series.p99.push(Math.round(parseFloat(String(row[p99Index])) || 0));
    });

    return { timePoints, seriesData: series };
  }, [data]);

  const hasData = timePoints.length > 0;

  const chartSeries = useMemo(
    () => [
      {
        name: "Avg",
        data: seriesData.avg,
        color: SERIES_COLORS.avg,
      },
      {
        name: "P50",
        data: seriesData.p50,
        color: SERIES_COLORS.p50,
      },
      {
        name: "P95",
        data: seriesData.p95,
        color: SERIES_COLORS.p95,
      },
      {
        name: "P99",
        data: seriesData.p99,
        color: SERIES_COLORS.p99,
      },
    ],
    [seriesData]
  );

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
            <IconClock size={18} />
          </Box>
          <Box className={classes.headerContent}>
            <Text className={classes.title}>Latency Trend</Text>
            <Text className={classes.description}>
              Response time over time
            </Text>
          </Box>
        </Box>
        <ErrorAndEmptyState message="Failed to load latency trend. Please try again." />
      </Box>
    );
  }

  if (isLoading) {
    return (
      <Box className={classes.container}>
        <Box className={classes.header}>
          <Box className={classes.headerIcon}>
            <IconClock size={18} />
          </Box>
          <Box className={classes.headerContent}>
            <Text className={classes.title}>Latency Trend</Text>
            <Text className={classes.description}>
              Response time over time
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
            <IconClock size={18} />
          </Box>
          <Box className={classes.headerContent}>
            <Text className={classes.title}>Latency Trend</Text>
            <Text className={classes.description}>
              Response time over time
            </Text>
          </Box>
        </Box>
        <Box className={classes.emptyState}>
          <IconChartLine size={32} className={classes.emptyIcon} />
          <Text className={classes.emptyMessage}>
            No latency data available for this time range
          </Text>
        </Box>
      </Box>
    );
  }

  return (
    <Box className={classes.container}>
      <Box className={classes.header}>
        <Box className={classes.headerIcon}>
          <IconClock size={18} />
        </Box>
        <Box className={classes.headerContent}>
          <Text className={classes.title}>Latency Trend</Text>
          <Text className={classes.description}>
            Spot latency spikes and regressions
          </Text>
        </Box>
      </Box>

      <Box className={classes.chartContainer}>
        <LineChart
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
                valueFormatter: (value: number) => formatLatency(value),
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
              name: "Latency",
              nameTextStyle: { fontSize: 11 },
              axisLabel: {
                fontSize: 10,
                formatter: (value: number) => formatLatency(value),
              },
            },
            series: chartSeries.map((series) => ({
              name: series.name,
              type: "line",
              data: series.data,
              smooth: true,
              showSymbol: false,
              lineStyle: { width: 2 },
              itemStyle: { color: series.color },
            })),
          }}
        />
      </Box>
    </Box>
  );
};
