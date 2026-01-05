import classes from "../InteractionDetailsGraphs/InteractionDetailsGraphs.module.css";
import apdexLatencyClasses from "./ApdexWithLatencyGraph.module.css";
import { useMantineTheme } from "@mantine/core";
import { LineChart } from "../../../../../../components/Charts";
import { AbsoluteNumbersForGraphs } from "../AbsoluteNumbersForGraphs/AbsoluteNumbersForGraphs";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { DATE_FORMAT } from "../../../../../../constants";
import { createTooltipFormatter } from "../../../../../../components/Charts/Tooltip/Tooltip";
import { GraphDataProps } from "../ApdexGraph/ApdexGraph.interface";

dayjs.extend(utc);

export function ApdexWithLatencyGraph({
  className,
  graphData,
  metrics,
}: GraphDataProps) {
  const theme = useMantineTheme();

  const getGraphTitle = () => {
    return "Interaction Apdex Score";
  };
  const getEChartsSeries = () => {
    return [
      {
        name: "Apdex Score",
        type: "line",
        smooth: true,
        data: graphData?.map((d) => [d.timestamp, d.apdex]),
        itemStyle: { color: theme.colors.blue[6] },
        lineStyle: { width: 2 },
        symbol: "circle",
        symbolSize: 6,
        emphasis: {
          focus: "series",
          itemStyle: {
            borderColor: theme.colors.blue[6],
            borderWidth: 2,
          },
        },
      },
    ];
  };

  const option = {
    grid: { right: 24, top: 24, bottom: 50 },
    tooltip: {
      trigger: "axis",
      formatter: createTooltipFormatter({
        valueFormatter: (value: any) => {
          const numericValue = Array.isArray(value) ? value[1] : value;
          return Number(numericValue).toFixed(3);
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
        fontSize: 11,
        formatter: (value: number) => {
          const dateTime = new Date(value);
          return dayjs(dateTime).format(DATE_FORMAT);
        },
      },
    },
    yAxis: {
      type: "value",
      nameTextStyle: {
        fontSize: 12,
        fontFamily: theme.fontFamily,
      },
      min: 0,
      max: 1,
      interval: 0.25,
      axisLabel: { fontSize: 11 },
    },
    series: getEChartsSeries(),
  };

  const getLatencyFormattString = (latency: number | null | undefined) => {
    if (latency === null || latency === undefined) {
      return "N/A";
    }
    // if the latency is greater than 1000, then format it as seconds
    if (latency > 1000) {
      return (latency / 1000).toFixed(2) + "s";
    }
    return latency.toFixed(2) + "ms";
  };

  const formatMetric = (value: number | null | undefined, suffix: string = "") => {
    if (value === null || value === undefined) {
      return "N/A";
    }
    return value.toFixed(2) + suffix;
  };

  return (
    <div className={`${classes.graphContainer} ${className}`}>
      <div className={apdexLatencyClasses.graphTitleContainer}>
        <div className={classes.graphTitle}>{getGraphTitle()}</div>
      </div>

      <div className={classes.absoluteCardContainer}>
        <AbsoluteNumbersForGraphs
          data={formatMetric(metrics?.apdex)}
          title="Apdex Score"
          color={metrics?.apdex !== null ? "blue-6" : "gray-5"}
        />
        <AbsoluteNumbersForGraphs
          data={getLatencyFormattString(metrics?.p50)}
          title="p50 Latency"
          color={metrics?.p50 !== null ? "gray-7" : "gray-5"}
        />
        <AbsoluteNumbersForGraphs
          data={getLatencyFormattString(metrics?.p95)}
          title="p95 Latency"
          color={metrics?.p95 !== null ? "gray-7" : "gray-5"}
        />
        <AbsoluteNumbersForGraphs
          data={formatMetric(metrics?.frozenFrameRate, "%")}
          title="Frozen Frames"
          color={metrics?.frozenFrameRate !== null ? "gray-7" : "gray-5"}
        />
      </div>

      <LineChart option={option} height={220} withLegend={false} />
    </div>
  );
}
