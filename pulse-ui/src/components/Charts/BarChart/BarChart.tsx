import ReactECharts from "echarts-for-react";
import * as echarts from "echarts/core";
import {
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
} from "echarts/components";
import { BarChart as EBarChart } from "echarts/charts";
import { CanvasRenderer } from "echarts/renderers";
import { BarChartProps } from "./BarChart.interface";
import { useMemo } from "react";
import { useMantineTheme } from "@mantine/core";

echarts.use([
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  EBarChart,
  CanvasRenderer,
]);

export function BarChart({
  height = 250,
  style,
  option,
  withLegend = true,
  tooltipValueFormatter,
  ...props
}: BarChartProps) {
  const theme = useMantineTheme();

  const defaultOptions = useMemo(
    () => ({
      tooltip: {
        trigger: "axis",
        axisPointer: {
          type: "shadow",
        },
      },
      grid: {
        top: "20",
        left: "25",
        right: "25",
        bottom: "50",
        containLabel: true,
      },
      xAxis: {
        boundaryGap: true,
        axisLabel: {
          margin: 15,
          fontSize: 12,
          fontFamily: theme.fontFamily,
          color: theme.colors.gray[6],
        },
      },
      yAxis: {
        splitNumber: 4,
        axisLabel: {
          color: theme.colors.gray[6],
          fontFamily: theme.fontFamily,
        },
        splitLine: {
          show: true,
          lineStyle: {
            type: "dashed",
            width: 1.25,
          },
        },
      },
      ...(withLegend && {
        legend: {
          bottom: 10,
          right: 25,
          padding: [7, 10],
          itemGap: 15,
          textStyle: {
            fontSize: 14,
            color: theme.black,
            fontFamily: theme.fontFamily,
          },
          icon: "rect",
        },
      }),
    }),
    [theme, withLegend],
  );

  const mergedOptions = useMemo(
    () => ({
      ...defaultOptions,
      ...option,
      tooltip: {
        ...defaultOptions.tooltip,
        ...option?.tooltip,
      },
      ...(withLegend &&
        defaultOptions.legend &&
        option?.legend && {
          legend: {
            ...defaultOptions.legend,
            ...option.legend,
          },
        }),
      ...(!withLegend && { legend: { show: false } }),
      xAxis: Array.isArray(option?.xAxis)
        ? option.xAxis.map((xAxisItem: any) => ({
            ...defaultOptions.xAxis,
            ...xAxisItem,
            axisLabel: {
              ...defaultOptions.xAxis.axisLabel,
              ...xAxisItem?.axisLabel,
            },
          }))
        : {
            ...defaultOptions.xAxis,
            ...option?.xAxis,
            axisLabel: {
              ...defaultOptions.xAxis.axisLabel,
              ...option?.xAxis?.axisLabel,
            },
          },
      yAxis: Array.isArray(option?.yAxis)
        ? option.yAxis.map((yAxisItem: any) => ({
            ...defaultOptions.yAxis,
            ...yAxisItem,
            axisLabel: {
              ...defaultOptions.yAxis.axisLabel,
              ...yAxisItem?.axisLabel,
            },
            splitLine: {
              ...defaultOptions.yAxis.splitLine,
              ...yAxisItem?.splitLine,
              lineStyle: {
                ...defaultOptions.yAxis.splitLine.lineStyle,
                ...yAxisItem?.splitLine?.lineStyle,
              },
            },
          }))
        : {
            ...defaultOptions.yAxis,
            ...option?.yAxis,
            axisLabel: {
              ...defaultOptions.yAxis.axisLabel,
              ...option?.yAxis?.axisLabel,
            },
            splitLine: {
              ...defaultOptions.yAxis.splitLine,
              ...option?.yAxis?.splitLine,
              lineStyle: {
                ...defaultOptions.yAxis.splitLine.lineStyle,
                ...option?.yAxis?.splitLine?.lineStyle,
              },
            },
          },
    }),
    [defaultOptions, withLegend, option],
  );

  return (
    <ReactECharts
      echarts={echarts}
      style={{ height, width: "100%", ...style }}
      option={mergedOptions}
      {...props}
    />
  );
}
