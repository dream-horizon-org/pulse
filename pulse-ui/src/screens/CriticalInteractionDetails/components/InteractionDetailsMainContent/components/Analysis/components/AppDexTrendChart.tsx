// @ts-nocheck

import { Card, Text, Box, useMantineTheme } from "@mantine/core";
import { LineChart } from "../../../../../../../components/Charts";

/**
 * AppDex Trend Chart
 * Shows 30-day trend of AppDex score
 */
const AppDexTrendChart = ({ data }) => {
  const theme = useMantineTheme();
  const avgApdex = data.reduce((sum, d) => sum + d.apdex, 0) / data.length;

  const option = {
    xAxis: {
      type: "category",
      data: data.map((d) => d.date),
      axisLabel: { rotate: 20, fontSize: 11 },
    },
    yAxis: {
      type: "value",
      min: 0,
      max: 1,
      name: "AppDex Score",
      nameLocation: "middle",
      nameGap: 40,
      interval: 0.25,
    },
    series: [
      {
        name: "AppDex",
        data: data.map((d) => d.apdex),
        showSymbol: true,
        smooth: true,
        lineStyle: { width: 3, color: "#3b82f6" },
        itemStyle: { color: "#3b82f6" },
        markLine: {
          symbol: "none",
          lineStyle: { type: "dashed", color: "transparent" },
          label: { fontSize: 10, color: "#64748b" },
          data: [
            {
              yAxis: 0.94,
              name: "Excellent (0.94)",
              lineStyle: { type: "dashed", color: theme.colors.blue[6] },
            },
            {
              yAxis: 0.85,
              name: "Good (0.85)",
              lineStyle: { type: "dashed", color: theme.colors.yellow[6] },
            },
            {
              yAxis: 0.7,
              name: "Poor (0.70)",
              lineStyle: { type: "dashed", color: theme.colors.red[6] },
            },
          ],
        },
      },
    ],
  };

  return (
    <Card
      padding="md"
      withBorder
      style={{
        background: "linear-gradient(145deg, #ffffff 0%, #fafbfc 100%)",
        border: "1px solid rgba(14, 201, 194, 0.12)",
        borderRadius: "16px",
        boxShadow:
          "0 4px 12px rgba(14, 201, 194, 0.06), inset 0 1px 0 rgba(255, 255, 255, 0.8)",
        transition: "all 0.4s cubic-bezier(0.4, 0, 0.2, 1)",
        position: "relative",
        overflow: "hidden",
      }}
    >
      <Box style={{ marginBottom: 8 }}>
        <Text
          fw={700}
          style={{
            fontSize: "14px",
            color: "#0ba09a",
            letterSpacing: "-0.2px",
          }}
        >
          AppDex Trend (Last 30 Days)
        </Text>
        <Text c="dimmed" size="sm" style={{ fontSize: "12px" }}>
          Overall user satisfaction over time • Avg: {avgApdex.toFixed(2)}
        </Text>
      </Box>
      <Box style={{ height: 380 }}>
        <LineChart option={option} height={380} withLegend={false} />
      </Box>
    </Card>
  );
};

export default AppDexTrendChart;
