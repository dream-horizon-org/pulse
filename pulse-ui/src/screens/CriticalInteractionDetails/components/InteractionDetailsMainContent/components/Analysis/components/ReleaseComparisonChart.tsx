// @ts-nocheck

import { Card, Text, Box, Grid, useMantineTheme } from "@mantine/core";
import {
  BarChart,
  CustomToolTip,
} from "../../../../../../../components/Charts";

/**
 * Release Comparison Chart
 * Separate charts showing Apdex, Crashes, and ANR across app versions
 */
const ReleaseComparisonChart = ({ data }) => {
  const theme = useMantineTheme();

  const createChartOption = ({
    chartData,
    color,
    yAxisName,
    yAxisMin = null,
    yAxisMax = null,
    yAxisInterval = null,
    seriesName,
    yAxisLabelSuffix,
  }) => ({
    grid: { left: 48, right: 24, top: 16, bottom: 50 },
    tooltip: {
      ...CustomToolTip,
      axisPointer: { type: "shadow" },
    },
    xAxis: {
      type: "category",
      data: data.map((d) => d.version),
      axisLabel: { fontSize: 11, fontFamily: theme.fontFamily, rotate: 20 },
    },
    yAxis: {
      type: "value",
      name: yAxisName,
      nameGap: 28,
      nameTextStyle: {
        fontSize: 12,
        fontFamily: theme.fontFamily,
      },
      ...(yAxisMin !== null && { min: yAxisMin }),
      ...(yAxisMax !== null && { max: yAxisMax }),
      ...(yAxisInterval !== null && { interval: yAxisInterval }),
      axisLabel: {
        fontSize: 11,
        formatter: (value: number) => `${value}${yAxisLabelSuffix || ""}`,
      },
    },
    series: [
      {
        name: seriesName,
        type: "bar",
        data: chartData,
        itemStyle: { color },
        barMaxWidth: 50,
      },
    ],
  });

  const apdexOption = createChartOption({
    chartData: data.map((d) => d.apdex),
    color: theme.colors.blue[6],
    yAxisMin: 0,
    yAxisMax: 1,
    yAxisInterval: 0.25,
    seriesName: "Apdex Score",
  });

  const crashesOption = createChartOption({
    chartData: data.map((d) => d.crashes),
    color: theme.colors.red[6],
    seriesName: "Crashes",
    yAxisMin: 0,
    yAxisMax: 100,
    yAxisInterval: 25,
    yAxisLabelSuffix: "%",
  });

  const anrOption = createChartOption({
    chartData: data.map((d) => d.anr),
    color: theme.colors.orange[6],
    seriesName: "ANR",
    yAxisMin: 0,
    yAxisMax: 100,
    yAxisInterval: 25,
    yAxisLabelSuffix: "%",
  });

  const charts = [
    {
      title: "Apdex Score by Release",
      description: "User satisfaction across versions",
      option: apdexOption,
    },
    {
      title: "Crashes by Release",
      description: "Application crashes per version",
      option: crashesOption,
    },
    {
      title: "ANR by Release",
      description: "Application Not Responding events",
      option: anrOption,
    },
  ];

  return (
    <Grid gutter="sm">
      {charts.map((chart, index) => (
        <Grid.Col key={index} span={{ base: 12, md: 4 }}>
          <Card
            padding="md"
            withBorder
            radius="md"
            style={{
              background: "linear-gradient(145deg, #ffffff 0%, #fafbfc 100%)",
              border: "1px solid rgba(14, 201, 194, 0.12)",
              borderRadius: "16px",
              boxShadow:
                "0 4px 12px rgba(14, 201, 194, 0.06), inset 0 1px 0 rgba(255, 255, 255, 0.8)",
              transition: "all 0.4s cubic-bezier(0.4, 0, 0.2, 1)",
              height: "100%",
              position: "relative",
              overflow: "hidden",
            }}
          >
            <Text
              size="sm"
              fw={700}
              c="#0ba09a"
              mb={2}
              style={{ fontSize: "14px", letterSpacing: "-0.2px" }}
            >
              {chart.title}
            </Text>
            <Text c="dimmed" size="xs" mb="sm" style={{ fontSize: "12px" }}>
              {chart.description}
            </Text>
            <Box style={{ height: 240 }}>
              <BarChart option={chart.option} height={240} withLegend={false} />
            </Box>
          </Card>
        </Grid.Col>
      ))}
    </Grid>
  );
};

export default ReleaseComparisonChart;
