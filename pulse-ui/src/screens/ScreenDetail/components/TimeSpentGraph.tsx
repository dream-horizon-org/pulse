import { Text } from "@mantine/core";
import { LineChart, createTooltipFormatter } from "../../../components/Charts";
import { GraphCardSkeleton } from "../../../components/Skeletons";
import { ErrorAndEmptyState } from "../../../components/ErrorAndEmptyState";
import classes from "./EngagementGraph.module.css";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";

dayjs.extend(utc);

interface TimeSpentTrendData {
  timestamp: number;
  avgTimeSpent: number;
  avgLoadTime: number;
}

interface TimeSpentGraphProps {
  avgTimeSpent: number;
  avgLoadTime: number;
  trendData: TimeSpentTrendData[];
  isLoading?: boolean;
  error?: Error | null;
}

export function TimeSpentGraph({
  avgTimeSpent,
  avgLoadTime,
  trendData,
  isLoading = false,
  error = null,
}: TimeSpentGraphProps) {
  if (isLoading) {
    return <GraphCardSkeleton title="Average Time Spent" chartHeight={240} metricsCount={2} />;
  }


  if (error) {
    return (
      <div className={classes.graphCard}>
        <div className={classes.graphTitle}>Average Time Spent</div>
        <ErrorAndEmptyState
          message="Failed to load time spent data"
          description={error.message || "An error occurred while fetching data"}
        />
      </div>
    );
  }

  if (!trendData || trendData.length === 0) {
    return (
      <div className={classes.graphCard}>
        <div className={classes.graphTitle}>Average Time Spent</div>
        <ErrorAndEmptyState message="No data available" />
      </div>
    );
  }

  return (
    <div className={classes.graphCard}>
      <div className={classes.graphTitle}>Average Time Spent</div>
      <div className={classes.metricsGrid}>
        <div className={classes.metricCard}>
          <Text className={classes.metricLabel}>Avg Time Spent</Text>
          <Text className={classes.metricValue} style={{ color: "#0ec9c2" }}>
            {avgTimeSpent.toFixed(1)}s
          </Text>
        </div>
        <div className={classes.metricCard}>
          <Text className={classes.metricLabel}>Avg Load Time</Text>
          <Text className={classes.metricValue} style={{ color: "#0ba09a" }}>
            {avgLoadTime >= 1 ? `${avgLoadTime.toFixed(1)}s` : `${(avgLoadTime * 1000).toFixed(0)}ms`}
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
                  return `${parseFloat(numericValue).toFixed(1)}s`;
                },
                customHeaderFormatter: (axisValue: any) => {
                  if (axisValue && typeof axisValue === "number") {
                    return dayjs(axisValue).format("MMM DD, HH:mm");
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
              name: "Seconds",
              nameGap: 40,
              nameTextStyle: { fontSize: 11 },
              axisLabel: {
                fontSize: 10,
                formatter: (value: number) => `${value}s`,
              },
            },
            series: [
              {
                name: "Avg Time Spent",
                type: "line",
                smooth: true,
                data: trendData.map((d) => [d.timestamp, d.avgTimeSpent]),
                itemStyle: { color: "#0ec9c2" },
                lineStyle: { width: 2.5, color: "#0ec9c2" },
                symbol: "circle",
                symbolSize: 6,
              },
              {
                name: "Avg Load Time",
                type: "line",
                smooth: true,
                data: trendData.map((d) => [d.timestamp, d.avgLoadTime]),
                itemStyle: { color: "#0ba09a" },
                lineStyle: { width: 2.5, color: "#0ba09a" },
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
