import { Text } from "@mantine/core";
import { LineChart, createTooltipFormatter } from "../../../components/Charts";
import { GraphSkeleton } from "../../../components/GraphSkeleton";
import { ErrorAndEmptyState } from "../../../components/ErrorAndEmptyState";
import classes from "./EngagementGraph.module.css";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";

dayjs.extend(utc);

interface UserActiveTrendData {
  timestamp: number;
  dau: number;
  wau: number;
  mau: number;
}

interface UserActiveGraphProps {
  dau: number;
  wau: number;
  mau: number;
  trendData: UserActiveTrendData[];
  isLoading?: boolean;
  error?: Error | null;
}

export function UserActiveGraph({
  dau,
  wau,
  mau,
  trendData,
  isLoading = false,
  error = null,
}: UserActiveGraphProps) {
  if (isLoading) {
    return <GraphSkeleton title="Active Users" height={240} />;
  }

  if (error) {
    return (
      <div className={classes.graphCard}>
        <div className={classes.graphTitle}>Active Users</div>
        <ErrorAndEmptyState
          message="Failed to load active users data"
          description={error.message || "An error occurred while fetching data"}
        />
      </div>
    );
  }

  if (!trendData || trendData.length === 0) {
    return (
      <div className={classes.graphCard}>
        <div className={classes.graphTitle}>Active Users</div>
        <ErrorAndEmptyState message="No data available" />
      </div>
    );
  }

  return (
    <div className={classes.graphCard}>
      <div className={classes.graphTitle}>Active Users</div>
      <div className={classes.metricsGrid}>
        <div className={classes.metricCard}>
          <Text className={classes.metricLabel}>Daily users</Text>
          <Text className={classes.metricValue} style={{ color: "#0ec9c2" }}>
            {dau.toLocaleString()}
          </Text>
        </div>
        <div className={classes.metricCard}>
          <Text className={classes.metricLabel}>Weekly users</Text>
          <Text className={classes.metricValue} style={{ color: "#0ba09a" }}>
            {wau.toLocaleString()}
          </Text>
        </div>
        <div className={classes.metricCard}>
          <Text className={classes.metricLabel}>Monthly users</Text>
          <Text className={classes.metricValue} style={{ color: "#2c3e50" }}>
            {mau.toLocaleString()}
          </Text>
        </div>
      </div>
      <div className={classes.chartContainer}>
        <LineChart
          height={240}
          withLegend={true}
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
                    return dayjs(axisValue).format("MMM DD, YYYY");
                  }
                  return axisValue || "";
                },
              }),
            },
            legend: {
              bottom: -5,
              textStyle: { fontSize: 10 },
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
              name: "Users",
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
                name: "DAU",
                type: "line",
                smooth: true,
                data: trendData.map((d) => [d.timestamp, d.dau]),
                itemStyle: { color: "#0ec9c2" },
                lineStyle: { width: 2.5, color: "#0ec9c2" },
                symbol: "circle",
                symbolSize: 6,
              },
              {
                name: "WAU",
                type: "line",
                smooth: true,
                data: trendData.map((d) => [d.timestamp, d.wau]),
                itemStyle: { color: "#0ba09a" },
                lineStyle: { width: 2.5, color: "#0ba09a" },
                symbol: "circle",
                symbolSize: 6,
              },
              {
                name: "MAU",
                type: "line",
                smooth: true,
                data: trendData.map((d) => [d.timestamp, d.mau]),
                itemStyle: { color: "#2c3e50" },
                lineStyle: { width: 2.5, color: "#2c3e50" },
                symbol: "circle",
                symbolSize: 6,
              },
            ],
          }}
        />
      </div>
    </div>
  );
}
