import { Box, Stack, Text } from "@mantine/core";
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
  if (isLoading) {
    return <ChartSkeleton height={height} title={`${vitalName} Trend`} />;
  }

  if (error) {
    return <ErrorAndEmptyState message="Error loading trend data" description={error.message} />;
  }

  if (!data || data.length === 0) {
    return <ErrorAndEmptyState message="No data available" description="No trend data for this vital" />;
  }

  const threshold = WEB_VITAL_THRESHOLDS[vitalName];

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
        data: data.map((point) => [new Date(point.bucket).getTime(), point.p75]),
        type: "line" as const,
        name: `${vitalName} P75`,
        smooth: true,
      },
    ],
    markLine: {
      data: [
        {
          yAxis: threshold?.good,
          name: "Good",
          lineStyle: { color: "#12b886" },
        },
        {
          yAxis: threshold?.needsImprovement,
          name: "Needs Improvement",
          lineStyle: { color: "#fa5252" },
        },
      ],
    },
  };

  return (
    <Stack gap="md">
      <Text fw={600} size="md">
        {vitalName} Trend
      </Text>
      <Box style={{ height }}>
        <LineChart option={option} height={height} syncTooltips={false} zoom={true} />
      </Box>
    </Stack>
  );
}
