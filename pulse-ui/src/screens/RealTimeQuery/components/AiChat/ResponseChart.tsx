import { useMemo } from "react";
import { Box, Text } from "@mantine/core";
import { BarChart, LineChart, AreaChart, PieChart } from "../../../../components/Charts";
import { AiChartConfig } from "../../../../hooks/useAiQuery";
import classes from "./AiChat.module.css";

// Teal-centric palette that fits the app's brand
const CHART_COLORS = [
  "#0ec9c2",
  "#0ba09a",
  "#f59e0b",
  "#6366f1",
  "#ef4444",
  "#8b5cf6",
  "#ec4899",
  "#14b8a6",
];

interface ResponseChartProps {
  config: AiChartConfig;
  height?: number;
}

/**
 * Renders a chart from the AI-generated ChartConfig using the app's
 * existing ECharts-based chart components (BarChart, LineChart, etc.).
 */
export function ResponseChart({ config, height = 280 }: ResponseChartProps) {
  const { type, title, xAxisLabel, yAxisLabel, data } = config;

  // ── Pie chart has a unique shape ──
  const pieOption = useMemo(() => {
    if (type !== "pie") return null;
    const dataset = data.datasets[0];
    if (!dataset) return null;

    return {
      tooltip: { trigger: "item" as const },
      series: [
        {
          type: "pie" as const,
          radius: ["38%", "68%"],
          center: ["50%", "50%"],
          avoidLabelOverlap: true,
          itemStyle: { borderRadius: 4, borderColor: "#fff", borderWidth: 2 },
          label: { show: true, fontSize: 12 },
          data: data.labels.map((label, i) => ({
            name: label,
            value: dataset.values[i] ?? 0,
            itemStyle: { color: CHART_COLORS[i % CHART_COLORS.length] },
          })),
        },
      ],
    };
  }, [type, data]);

  // ── Bar / Line / Area common option ──
  const cartesianOption = useMemo(() => {
    if (type === "pie") return null;

    const series = data.datasets.map((ds, i) => ({
      name: ds.name,
      type: type === "area" ? ("line" as const) : (type as "bar" | "line"),
      data: ds.values,
      smooth: type === "line" || type === "area",
      ...(type === "area" && { areaStyle: { opacity: 0.25 } }),
      ...(type === "bar" && { barMaxWidth: 40 }),
      itemStyle: { color: CHART_COLORS[i % CHART_COLORS.length] },
    }));

    return {
      tooltip: { trigger: "axis" as const },
      xAxis: {
        type: "category" as const,
        data: data.labels,
        ...(xAxisLabel && { name: xAxisLabel }),
        axisLabel: {
          rotate: data.labels.length > 6 ? 25 : 0,
          fontSize: 11,
        },
      },
      yAxis: {
        type: "value" as const,
        ...(yAxisLabel && { name: yAxisLabel }),
      },
      series,
    };
  }, [type, data, xAxisLabel, yAxisLabel]);

  // Pick the right chart component
  const renderChart = () => {
    switch (type) {
      case "pie":
        return pieOption ? (
          <PieChart option={pieOption} height={height} withLegend />
        ) : null;
      case "bar":
        return cartesianOption ? (
          <BarChart option={cartesianOption} height={height} withLegend={data.datasets.length > 1} />
        ) : null;
      case "line":
        return cartesianOption ? (
          <LineChart
            option={cartesianOption}
            height={height}
            withLegend={data.datasets.length > 1}
            zoom={false}
            syncTooltips={false}
          />
        ) : null;
      case "area":
        return cartesianOption ? (
          <AreaChart
            option={cartesianOption}
            height={height}
            withLegend={data.datasets.length > 1}
            zoom={false}
            syncTooltips={false}
          />
        ) : null;
      default:
        return null;
    }
  };

  return (
    <Box className={classes.chartContainer}>
      {title && (
        <Text size="xs" fw={600} c="dimmed" tt="uppercase" mb="sm">
          {title}
        </Text>
      )}
      <Box className={classes.chartWrapper}>{renderChart()}</Box>
    </Box>
  );
}

