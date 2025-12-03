import { Text } from "@mantine/core";
import {
  createTooltipFormatter,
  LineChart,
} from "../../../../components/Charts";
import classes from "./UserEngagementGraph.module.css";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { useMemo } from "react";
import { UserEngagementGraphProps } from "./UserEngagementGraph.interface";
import { useGetUserEngagementData } from "../../../../hooks/useGetUserEngagementData";

dayjs.extend(utc);

export function UserEngagementGraph({
  screenName,
  appVersion,
  osVersion,
  device,
  startTime,
  endTime,
  spanType = "app_start",
}: UserEngagementGraphProps = {}) {
  // Always use last 7 days for daily graph (ignore time filter)
  const { dailyStartDate, dailyEndDate } = useMemo(() => {
    const end = dayjs().utc().endOf("day");
    const start = end.subtract(6, "days").startOf("day");
    return {
      dailyStartDate: start.toISOString(),
      dailyEndDate: end.toISOString(),
    };
  }, []);

  // Always use last 1 month for weekly and monthly averages (ignore time filter)
  const { weekStartDate, weekEndDate } = useMemo(() => {
    const end = dayjs().utc().endOf("day");
    const start = end.subtract(6, "days").startOf("day");
    return {
      weekStartDate: start.toISOString(),
      weekEndDate: end.toISOString(),
    };
  }, []);

  const { monthStartDate, monthEndDate } = useMemo(() => {
    const end = dayjs().utc().endOf("day");
    const start = end.subtract(27, "days").startOf("day");
    return {
      monthStartDate: start.toISOString(),
      monthEndDate: end.toISOString(),
    };
  }, []);

  const { data } = useGetUserEngagementData({
    screenName,
    appVersion,
    osVersion,
    device,
    dailyStartDate,
    dailyEndDate,
    weekStartDate,
    weekEndDate,
    monthStartDate,
    monthEndDate,
    spanType,
  });

  const { dailyUsers, weeklyUsers, monthlyUsers, trendData } = data;

  return (
    <div className={classes.graphCard}>
      <div className={classes.graphTitle}>User Engagement</div>
      <div className={classes.metricsGrid}>
        <div className={classes.metricCard}>
          <Text className={classes.metricLabel}>Avg Daily Users</Text>
          <Text className={classes.metricValue} style={{ color: "#0ec9c2" }}>
            {dailyUsers.toLocaleString()}
          </Text>
        </div>
        <div className={classes.metricCard}>
          <Text className={classes.metricLabel}>Weekly Users</Text>
          <Text className={classes.metricValue} style={{ color: "#0ba09a" }}>
            {weeklyUsers.toLocaleString()}
          </Text>
        </div>
        <div className={classes.metricCard}>
          <Text className={classes.metricLabel}>Monthly Users</Text>
          <Text className={classes.metricValue} style={{ color: "#2c3e50" }}>
            {monthlyUsers.toLocaleString()}
          </Text>
        </div>
      </div>
      <div className={classes.chartContainer}>
        <LineChart
          height={260}
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
              name: "Users",
              nameGap: 40,
              nameTextStyle: { fontSize: 11 },
              axisLabel: {
                fontSize: 10,
                formatter: (value: number) => `${(value / 1000).toFixed(0)}K`,
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
            ],
          }}
        />
      </div>
    </div>
  );
}
