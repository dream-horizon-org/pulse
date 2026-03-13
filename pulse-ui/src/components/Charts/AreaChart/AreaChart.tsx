import ReactECharts from "echarts-for-react";
import * as echarts from "echarts/core";
import {
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  DataZoomComponent,
  BrushComponent,
} from "echarts/components";
import { LineChart } from "echarts/charts";
import { CanvasRenderer } from "echarts/renderers";
import { AreaChartProps } from "./AreaChart.interface";
import { useRef, useEffect, useMemo } from "react";
import { LineSeriesOption } from "echarts";
import { useChartReady, useChartOptions, useTooltip } from "../hooks";

echarts.use([
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  LineChart,
  CanvasRenderer,
  DataZoomComponent,
  BrushComponent,
]);

export function AreaChart({
  height = 250,
  style,
  option,
  zoom = true,
  withLegend = true,
  tooltipValueFormatter,
  syncTooltips = true,
  group = "defaultChartGroup",
  ...props
}: AreaChartProps) {
  const chartRef = useRef<ReactECharts>(null);

  const { onChartReady } = useChartReady({
    syncTooltips,
    group,
    enableBrushSelection: true,
  });

  const tooltip = useTooltip({ tooltipValueFormatter });

  useEffect(() => {
    if (syncTooltips && chartRef.current) {
      const chartInstance = chartRef.current.getEchartsInstance();
      echarts.connect(group);
      const chartDom = chartInstance.getDom();
      const showTooltip = () =>
        chartInstance.setOption({ tooltip: { show: true } });
      const hideTooltip = () =>
        chartInstance.setOption({ tooltip: { show: false } });
      chartDom.addEventListener("mouseenter", showTooltip);
      chartDom.addEventListener("mouseleave", hideTooltip);
      return () => {
        echarts.disconnect(group);
        chartDom.removeEventListener("mouseenter", showTooltip);
        chartDom.removeEventListener("mouseleave", hideTooltip);
      };
    }
  }, [syncTooltips, group]);

  const { createMergedOptions } = useChartOptions({
    tooltip,
    withLegend,
    zoom,
    chartType: "area",
  });

  const mergedOptions = useMemo(
    () => ({
      ...createMergedOptions(option),
      series:
        option?.series?.map((seriesItem: LineSeriesOption) => {
          const areaStyle =
            seriesItem.color && !seriesItem.areaStyle?.color
              ? { color: `${seriesItem.color}30` }
              : {};

          return {
            type: "line",
            areaStyle: { ...areaStyle, ...seriesItem.areaStyle },
            emphasis: { focus: "series" },
            showSymbol: false,
            ...seriesItem,
          };
        }) || [],
    }),
    [createMergedOptions, option],
  );

  return (
    <ReactECharts
      ref={chartRef}
      echarts={echarts}
      style={{ height, width: "100%", ...style }}
      option={mergedOptions}
      onChartReady={
        onChartReady as unknown as React.ComponentProps<
          typeof ReactECharts
        >["onChartReady"]
      }
      {...props}
    />
  );
}
