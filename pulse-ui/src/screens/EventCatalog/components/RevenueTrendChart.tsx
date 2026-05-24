import ReactECharts from "echarts-for-react";
import * as echarts from "echarts/core";
import {
  GridComponent,
  TooltipComponent,
} from "echarts/components";
import { LineChart } from "echarts/charts";
import { CanvasRenderer } from "echarts/renderers";
import { useMemo } from "react";
import dayjs from "dayjs";
import { DailyPoint } from "../hooks/useRevenueEventPreview";
import classes from "./RevenueTrendChart.module.css";

echarts.use([GridComponent, TooltipComponent, LineChart, CanvasRenderer]);

export type ChartMode = "volume" | "aov";

type RevenueTrendChartProps = {
  points: DailyPoint[];
  currency?: string;
  mode: ChartMode;
  height?: number;
};

function formatDayLabel(iso: string): string {
  const d = dayjs(iso);
  return d.isValid() ? d.format("MMM D") : iso;
}

function formatMoney(value: number, currency?: string): string {
  const formatted = value.toLocaleString(undefined, {
    maximumFractionDigits: value >= 1000 ? 0 : 2,
  });
  if (currency === "INR") {
    return `₹${formatted}`;
  }
  if (currency) {
    return `${currency} ${formatted}`;
  }
  return formatted;
}

export function RevenueTrendChart({
  points,
  currency,
  mode,
  height = 160,
}: RevenueTrendChartProps) {
  const option = useMemo(() => {
    const labels = points.map((p) => formatDayLabel(p.date));
    const values =
      mode === "aov"
        ? points.map((p) => p.avgValue)
        : points.map((p) => p.eventCount);

    return {
      tooltip: {
        show: true,
        trigger: "axis",
        confine: true,
        backgroundColor: "#fff",
        borderColor: "rgba(14, 201, 194, 0.45)",
        borderWidth: 1,
        padding: [10, 12],
        textStyle: { color: "#212529", fontSize: 12 },
        formatter: (params: unknown) => {
          const items = Array.isArray(params) ? params : [params];
          const idx = (items[0] as { dataIndex?: number })?.dataIndex ?? 0;
          const point = points[idx];
          if (!point) {
            return "";
          }
          const day = formatDayLabel(point.date);
          if (mode === "volume") {
            return [
              `<strong>${day}</strong>`,
              `Events: ${point.eventCount.toLocaleString()}`,
            ].join("<br/>");
          }
          return [
            `<strong>${day}</strong>`,
            `AOV: ${formatMoney(point.avgValue, currency)}`,
            `Events: ${point.eventCount.toLocaleString()}`,
          ].join("<br/>");
        },
      },
      grid: {
        left: 4,
        right: 8,
        top: 16,
        bottom: 28,
      },
      xAxis: {
        type: "category",
        data: labels,
        boundaryGap: false,
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: {
          fontSize: 10,
          color: "#868e96",
          interval: points.length > 14 ? 1 : 0,
        },
      },
      yAxis: {
        type: "value",
        show: false,
      },
      series: [
        {
          type: "line",
          name: mode === "aov" ? "AOV" : "Events",
          data: values,
          smooth: true,
          showSymbol: true,
          symbol: "circle",
          symbolSize: 7,
          color: "#0ec9c2",
          lineStyle: { width: 2.5 },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: "rgba(14, 201, 194, 0.35)" },
              { offset: 1, color: "rgba(14, 201, 194, 0.02)" },
            ]),
          },
          emphasis: {
            focus: "series",
            scale: true,
            itemStyle: {
              borderWidth: 2,
              borderColor: "#fff",
              shadowBlur: 6,
              shadowColor: "rgba(14, 201, 194, 0.45)",
            },
          },
        },
      ],
    };
  }, [points, currency, mode]);

  if (points.length === 0) {
    return null;
  }

  return (
    <div className={classes.chartRoot}>
      <ReactECharts
        echarts={echarts}
        option={option}
        style={{ height, width: "100%" }}
        notMerge
      />
      <p className={classes.chartHint}>Hover or tap a point for day details</p>
    </div>
  );
}
