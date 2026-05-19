import { Box, Stack, Text, useMantineTheme } from "@mantine/core";
import { VitalTrendChartProps } from "./VitalTrendChart.interface";
import { LineChart } from "../../../../components/Charts/LineChart/LineChart";
import { ChartSkeleton } from "../../../../components/Skeletons/ChartSkeleton";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState/ErrorAndEmptyState";
import { WEB_VITAL_THRESHOLDS } from "../../WebVitals.constants";

export function VitalTrendChart({
  vitalName,
  data,
  isLoading,
  error,
  height = 320,
}: VitalTrendChartProps) {
  const theme = useMantineTheme();

  if (isLoading) {
    return <ChartSkeleton height={height} title={`${vitalName} Trend`} />;
  }

  if (error) {
    return (
      <ErrorAndEmptyState
        message="Error loading trend data"
        description={error.message}
      />
    );
  }

  if (!data || data.length === 0) {
    return (
      <ErrorAndEmptyState
        message="No data available"
        description="No trend data for this vital"
      />
    );
  }

  const threshold = WEB_VITAL_THRESHOLDS[vitalName];

  const goodLineColor = theme.colors.green[6];
  const poorBoundaryColor = theme.colors.red[6];

  const markLineData = [
    ...(threshold?.good != null
      ? [
          {
            yAxis: threshold.good,
            name: "Good",
            lineStyle: { color: goodLineColor },
          },
        ]
      : []),
    ...(threshold?.needsImprovement != null
      ? [
          {
            yAxis: threshold.needsImprovement,
            name: "Needs Improvement",
            lineStyle: { color: poorBoundaryColor },
          },
        ]
      : []),
  ];

  const seriesColor = theme.colors.primary[6];

  const option = {
    xAxis: {
      type: "time" as const,
      boundaryGap: false,
    },
    yAxis: {
      type: "value" as const,
    },
    series: [
      {
        data: data.map((point) => [
          new Date(point.bucket).getTime(),
          point.p75,
        ]),
        type: "line" as const,
        name: `${vitalName} P75`,
        smooth: true,
        itemStyle: { color: seriesColor },
        lineStyle: { color: seriesColor },
      },
    ],
    ...(markLineData.length > 0 ? { markLine: { data: markLineData } } : {}),
  };

  return (
    <Stack gap="md">
      <Text fw={700} size="lg">
        {vitalName} Trend
      </Text>
      <Box style={{ height }}>
        <LineChart
          option={option}
          height={height}
          syncTooltips={false}
          zoom={true}
          enableBrushSelection={false}
        />
      </Box>
    </Stack>
  );
}
