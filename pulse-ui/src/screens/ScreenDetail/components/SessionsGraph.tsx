import { Text } from "@mantine/core";
import { LineChart, createTooltipFormatter } from "../../../components/Charts";
import { GraphSkeleton } from "../../../components/GraphSkeleton";
import { ErrorAndEmptyState } from "../../../components/ErrorAndEmptyState";
import classes from "./EngagementGraph.module.css";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";

dayjs.extend(utc);

interface SessionsTrendData {
  timestamp: number;
  sessionCount: number;
}

interface SessionsGraphProps {
  totalSessions: number;
  trendData: SessionsTrendData[];
  isLoading?: boolean;
  error?: Error | null;
}

export function SessionsGraph({
  totalSessions,
  trendData,
  isLoading = false,
  error = null,
}: SessionsGraphProps) {
  if (isLoading) {
    return <GraphSkeleton title="Sessions" height={240} />;
  }

  if (error) {
    return (
      <div className={classes.graphCard}>
        <div className={classes.graphTitle}>Sessions</div>
        <ErrorAndEmptyState
          message="Failed to load sessions data"
          description={error.message || "An error occurred while fetching data"}
        />
      </div>
    );
  }

  if (!trendData || trendData.length === 0) {
    return (
      <div className={classes.graphCard}>
        <div className={classes.graphTitle}>Sessions</div>
        <ErrorAndEmptyState message="No data available" />
      </div>
    );
  }

  return (
    <div className={classes.graphCard}>
      <div className={classes.graphTitle}>Sessions</div>
      <div className={classes.metricsGrid}>
        <div className={classes.metricCard}>
          <Text className={classes.metricLabel}>Total Sessions</Text>
          <Text className={classes.metricValue} style={{ color: "#f59e0b" }}>
            {totalSessions.toLocaleString()}
          </Text>
        </div>
      </div>
      <div className={classes.chartContainer}>
        <LineChart
          height={240}
          withLegend={false}
          option={{
            grid: { left: 60, right: 24, top: 24, bottom: 45 },
            tooltip: {
              trigger: "axis",
              formatter: createTooltipFormatter({
                valueFormatter: (value: any) => {
                  const numericValue = Array.isArray(value) ? value[1] : value;
                  return `${parseFloat(numericValue).toFixed(0)}`;
                },
                customHeaderFormatter: (axisValue: any) => {
                  if (axisValue && typeof axisValue === "number") {
                    return dayjs(axisValue).format("MMM DD, HH:mm");
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
                formatter: (value: number) => {
                  if (value >= 1000) {
                    return `${(value / 1000).toFixed(0)}K`;
                  }
                  return value.toString();
                },
              },
            },
            series: [
              {
                name: "Sessions",
                type: "line",
                smooth: true,
                data: trendData.map((d) => [d.timestamp, d.sessionCount]),
                itemStyle: { color: "#f59e0b" },
                lineStyle: { width: 2.5, color: "#f59e0b" },
                symbol: "circle",
                symbolSize: 6,
                areaStyle: {
                  color: {
                    type: "linear",
                    x: 0,
                    y: 0,
                    x2: 0,
                    y2: 1,
                    colorStops: [
                      { offset: 0, color: "rgba(245, 158, 11, 0.3)" },
                      { offset: 1, color: "rgba(245, 158, 11, 0.05)" },
                    ],
                  },
                },
              },
            ],
          }}
        />
      </div>
    </div>
  );
}
