import ReactECharts from "echarts-for-react";
import * as echarts from "echarts/core";
import {
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  DataZoomComponent,
} from "echarts/components";
import { LineChart as EChartsLineChart } from "echarts/charts";
import { CanvasRenderer } from "echarts/renderers";
import { SparklineChartProps } from "./SparklineChart.interface";
import { rgba } from "@mantine/core";
import { useMemo } from "react";
import { LineSeriesOption } from "echarts";

echarts.use([
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  EChartsLineChart,
  CanvasRenderer,
  DataZoomComponent,
]);

export function SparklineChart({
  height = 250,
  style,
  option,
  zoom = true,
  fillOpacity = 0.8,
  withGradient = true,
  strokeWidth = 1.9,
  ...props
}: SparklineChartProps) {
  const defaultOptions = useMemo(
    () => ({
      tooltip: {
        show: false,
      },
      grid: {
        left: 0,
        right: 0,
        top: 0,
        bottom: 0,
      },
      xAxis: {
        show: false,
        type: "category",
        boundaryGap: false,
      },
      yAxis: {
        show: false,
        type: "value",
      },
      ...(zoom && {
        dataZoom: [
          {
            type: "inside",
            start: 0,
            end: 100,
            minValueSpan: 2,
            zoomOnMouseWheel: true,
            moveOnMouseMove: true,
            moveOnMouseWheel: false,
            preventDefaultMouseMove: false,
          },
        ],
      }),
    }),
    [zoom],
  );

  const mergedOptions = useMemo(
    () => ({
      ...defaultOptions,
      ...option,
      tooltip: {
        ...defaultOptions.tooltip,
        ...option?.tooltip,
      },
      grid: {
        ...defaultOptions.grid,
        ...option?.grid,
      },
      xAxis: {
        ...defaultOptions.xAxis,
        ...option?.xAxis,
      },
      yAxis: {
        ...defaultOptions.yAxis,
        ...option?.yAxis,
      },
      series:
        option?.series?.map((seriesItem: LineSeriesOption) => {
          const seriesColor = withGradient
            ? new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: seriesItem.color as string },
                { offset: 1, color: rgba(seriesItem.color as string, 0) },
              ])
            : seriesItem.color;
          const areaStyle =
            seriesItem.color && !seriesItem.areaStyle?.color
              ? { color: seriesColor, opacity: fillOpacity }
              : {};

          return {
            type: "line",
            emphasis: { focus: "series" },
            showSymbol: false,
            areaStyle,
            lineStyle: {
              width: strokeWidth,
            },
            ...seriesItem,
          };
        }) || [],
      ...(zoom && option?.dataZoom && { dataZoom: option.dataZoom }),
      ...(!zoom && { dataZoom: undefined }),
    }),
    [defaultOptions, option, zoom, fillOpacity, withGradient, strokeWidth],
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
