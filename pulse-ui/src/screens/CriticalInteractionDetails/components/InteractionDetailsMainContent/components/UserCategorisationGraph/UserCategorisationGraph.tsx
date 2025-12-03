import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import classes from "../InteractionDetailsGraphs/InteractionDetailsGraphs.module.css";
import { GraphDataProps } from "../ApdexGraph/ApdexGraph.interface";
import { AbsoluteNumbersForGraphs } from "../AbsoluteNumbersForGraphs";
import { useMantineTheme } from "@mantine/core";
import { DATE_FORMAT } from "../../../../../../constants";
import { AreaChart } from "../../../../../../components/Charts";
import { createTooltipFormatter } from "../../../../../../components/Charts/Tooltip";

dayjs.extend(utc);

const getPercentage = (value: number, total: number) => {
  if (total === 0) return 0;
  return (value / total) * 100;
};

export function UserCategorisationGraph({
  graphData,
  metrics,
  height,
}: GraphDataProps) {
  const theme = useMantineTheme();

  return (
    <div className={classes.graphContainer}>
      <div className={classes.graphTitle}>User Experience Distribution</div>
      <div className={classes.absoluteCardContainer}>
        <AbsoluteNumbersForGraphs
          data={metrics?.excellentUsersPercentage || "0"}
          title="Excellent"
          color="green-6"
        />
        <AbsoluteNumbersForGraphs
          data={metrics?.goodUsersPercentage || "0"}
          title="Good"
          color="yellow-6"
        />
        <AbsoluteNumbersForGraphs
          data={metrics?.averageUsersPercentage || "0"}
          title="Average"
          color="orange-6"
        />
        <AbsoluteNumbersForGraphs
          data={metrics?.poorUsersPercentage || "0"}
          title="Poor"
          color="red-9"
        />
      </div>
      <AreaChart
        // height={300}
        height={height ?? 300}
        withLegend={false}
        option={{
          grid: {
            top: "20",
            left: "35",
            right: "15",
            bottom: "24",
            containLabel: true,
          },
          tooltip: {
            trigger: "axis",
            formatter: createTooltipFormatter({
              valueFormatter: (value: any) => {
                const numericValue = Array.isArray(value) ? value[1] : value;
                return `${parseFloat(numericValue).toFixed(1)}%`;
              },
              customHeaderFormatter: (axisValue: any) => {
                if (axisValue && typeof axisValue === "number") {
                  return dayjs(axisValue).format(DATE_FORMAT);
                }
                return axisValue || "";
              },
            }),
          },
          xAxis: {
            type: "time",
            axisLabel: {
              fontSize: 10,
              formatter: (value: number) => dayjs(value).format(DATE_FORMAT),
            },
          },
          yAxis: {
            type: "value",
            min: 0,
            max: 100,
            axisLabel: {
              formatter: "{value}%",
              fontSize: 10,
            },
            interval: 25,
          },
          series: [
            {
              name: "Excellent",
              stack: "total",
              color: theme.colors.green[9],
              data: graphData?.map((d) => [
                dayjs(d.timestamp).valueOf(),
                // calculate the percentage of the excellent users
                getPercentage(
                  d?.userExcellent,
                  d?.userExcellent + d?.userGood + d?.userAvg + d?.userPoor,
                ),
              ]),
            },
            {
              name: "Good",
              stack: "total",
              color: theme.colors.yellow[3],
              data: graphData?.map((d) => [
                dayjs(d.timestamp).valueOf(),
                // calculate the percentage of the good users
                getPercentage(
                  d?.userGood,
                  d?.userExcellent + d?.userGood + d?.userAvg + d?.userPoor,
                ),
              ]),
            },
            {
              name: "Average",
              stack: "total",
              color: theme.colors.orange[6],
              data: graphData?.map((d) => [
                dayjs(d.timestamp).valueOf(),
                // calculate the percentage of the average users
                getPercentage(
                  d?.userAvg,
                  d?.userExcellent + d?.userGood + d?.userAvg + d?.userPoor,
                ),
              ]),
            },
            {
              name: "Poor",
              stack: "total",
              color: theme.colors.red[9],
              data: graphData?.map((d) => [
                dayjs(d.timestamp).valueOf(),
                // calculate the percentage of the poor users
                getPercentage(
                  d?.userPoor,
                  d?.userExcellent + d?.userGood + d?.userAvg + d?.userPoor,
                ),
              ]),
            },
          ],
        }}
      />
    </div>
  );
}
