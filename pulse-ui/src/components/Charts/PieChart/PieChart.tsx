import ReactECharts from "echarts-for-react";
import * as echarts from "echarts/core";
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
} from "echarts/components";
import { PieChart as EPieChart } from "echarts/charts";
import { CanvasRenderer } from "echarts/renderers";
import { PieChartProps } from "./PieChart.interface";
import { useMemo } from "react";
import { useMantineTheme } from "@mantine/core";

echarts.use([
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  EPieChart,
  CanvasRenderer,
]);

export function PieChart({
  height = 250,
  style,
  option,
  withLegend = true,
  tooltipValueFormatter,
  ...props
}: PieChartProps) {
  const theme = useMantineTheme();

  const defaultOptions = useMemo(
    () => ({
      tooltip: {
        trigger: "item",
      },
      ...(withLegend && {
        legend: {
          bottom: 0,
          left: "center",
          textStyle: {
            fontSize: 14,
            color: theme.black,
            fontFamily: theme.fontFamily,
          },
          icon: "circle",
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
