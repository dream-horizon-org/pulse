import { Group, Text, Tooltip } from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import {
  createTooltipFormatter,
  AreaChart,
} from "../../../../components/Charts";
import classes from "./ActiveSessionsGraph.module.css";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { useMemo } from "react";
import { ActiveSessionsGraphProps } from "./ActiveSessionsGraph.interface";
import { useGetActiveSessionsData } from "../../../../hooks/useGetActiveSessionsData";
import { getTimeBucketSize } from "../../../../utils/TimeBucketUtil";
import {
  ChartSkeleton,
  GraphCardSkeleton,
  SkeletonLoader,
} from "../../../../components/Skeletons";

dayjs.extend(utc);

export function ActiveSessionsGraph({
  screenName,
  appVersion,
  osVersion,
  device,
  startTime,
  endTime,
  onTimeFilterChange,
}: ActiveSessionsGraphProps = {}) {
  const { startDate, endDate, bucketSize } = useMemo(() => {
    let finalStartDate: string;
    let finalEndDate: string;

    if (startTime && endTime) {
      finalStartDate = dayjs.utc(startTime).toISOString();
      finalEndDate = dayjs.utc(endTime).toISOString();
    } else {
      const end = dayjs().utc().endOf("day");
      const start = end.subtract(6, "days").startOf("day");
      finalStartDate = start.toISOString();
      finalEndDate = end.toISOString();
    }

    const bucket = getTimeBucketSize(finalStartDate, finalEndDate);

    return {
      startDate: finalStartDate,
      endDate: finalEndDate,
      bucketSize: bucket,
    };
  }, [startTime, endTime]);

  const { data, loading, failed } = useGetActiveSessionsData({
    screenName,
    appVersion,
    osVersion,
    device,
    startTime: startDate,
    endTime: endDate,
    bucketSize,
  });

  const { currentSessions, peakSessions, averageSessions, trendData, hasData } =
    data;
  const isAnyLoading = loading.current || loading.trend;

  if (!hasData && isAnyLoading) {
    return (
      <GraphCardSkeleton
        title="Active Sessions"
        chartHeight={260}
        metricsCount={3}
      />
    );
  }

  const formatMetricValue = (
    value: number | null,
    color: string,
    isMetricLoading: boolean,
    isMetricFailed: boolean,
  ) => {
    if (isMetricLoading) {
      return (
        <SkeletonLoader
          height={30}
          width="55%"
          radius="sm"
          className={classes.metricValueSkeleton}
        />
      );
    }

    if (isMetricFailed || value === null) {
      return (
        <Text className={classes.metricValue} c="dimmed">
          N/A
        </Text>
      );
    }

    return (
      <Text className={classes.metricValue} style={{ color }}>
        {value.toLocaleString()}
      </Text>
    );
  };

  return (
    <div className={classes.graphCard}>
      <div className={classes.graphTitle}>Active Sessions</div>
      <div className={classes.metricsGrid}>
        <div className={classes.metricCard}>
          <Group gap={4} wrap="nowrap" align="center" justify="center">
            <Text className={classes.metricLabel}>Concurrent Sessions</Text>
            <Tooltip
              label="Unique sessions active in the last 5 minutes."
              withArrow
              multiline
              w={200}
            >
              <IconInfoCircle
                size={13}
                style={{ opacity: 0.5, cursor: "help", flexShrink: 0 }}
              />
            </Tooltip>
          </Group>
          {formatMetricValue(
            currentSessions,
            "#0ec9c2",
            loading.current,
            failed.current,
          )}
        </div>
        <div className={classes.metricCard}>
          <Text className={classes.metricLabel}>Peak</Text>
          {formatMetricValue(
            peakSessions,
            "#0ba09a",
            loading.trend,
            failed.trend,
          )}
        </div>
        <div className={classes.metricCard}>
          <Text className={classes.metricLabel}>Average</Text>
          {formatMetricValue(
            averageSessions,
            "#2c3e50",
            loading.trend,
            failed.trend,
          )}
        </div>
      </div>
      <div className={classes.chartContainer}>
        {loading.trend ? (
          <ChartSkeleton height={260} />
        ) : failed.trend ? (
          <Text size="sm" c="dimmed" ta="center" py="xl">
            Chart unavailable
          </Text>
        ) : (
          <AreaChart
            height={260}
            withLegend={false}
            onTimeFilterChange={onTimeFilterChange}
            syncDataZoomToTimeFilter={Boolean(onTimeFilterChange)}
            option={{
              grid: { left: 60, right: 24, top: 24, bottom: 45 },
              tooltip: {
                trigger: "axis",
                formatter: createTooltipFormatter({
                  valueFormatter: (value: any) => {
                    const numericValue = Array.isArray(value)
                      ? value[1]
                      : value;
                    return `${parseFloat(numericValue).toFixed(0)}`;
                  },
                  customHeaderFormatter: (axisValue: any) => {
                    if (axisValue && typeof axisValue === "number") {
                      return dayjs(axisValue).format("MMM DD, YYYY");
                    }
                    return axisValue || "";
                  },
                }),
              },
              xAxis: {
                type: "time",
                axisLabel: {
                  fontSize: 10,
                  formatter: (value: number) => dayjs(value).format("MMM DD"),
                },
              },
              yAxis: {
                type: "value",
                name: "Sessions",
                nameGap: 40,
                nameTextStyle: { fontSize: 11 },
                axisLabel: {
                  fontSize: 10,
                  formatter: (value: number) => `${(value / 1000).toFixed(1)}K`,
                },
              },
              series: [
                {
                  name: "Sessions",
                  type: "line",
                  smooth: true,
                  areaStyle: {
                    color: {
                      type: "linear",
                      x: 0,
                      y: 0,
                      x2: 0,
                      y2: 1,
                      colorStops: [
                        { offset: 0, color: "rgba(14, 201, 194, 0.4)" },
                        { offset: 1, color: "rgba(14, 201, 194, 0.05)" },
                      ],
                    },
                  },
                  data: trendData.map((d) => [d.timestamp, d.sessions]),
                  itemStyle: { color: "#0ec9c2" },
                  lineStyle: { width: 2.5, color: "#0ec9c2" },
                  symbol: "circle",
                  symbolSize: 6,
                },
              ],
            }}
          />
        )}
      </div>
    </div>
  );
}
