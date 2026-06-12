import { MantineTheme } from "@mantine/core";

interface Threshold {
  threshold: number;
  metric: string;
}

interface ThresholdLabelConfig {
  label: string;
}

export const createMarkLineConfig = (
  thresholds: Threshold[],
  theme: MantineTheme,
  getThresholdColorAndLabel: (metric: string) => ThresholdLabelConfig,
  getThreshold?: (metric: any, threshold: number) => number,
) => ({
  symbol: "none",
  lineStyle: {
    type: "dashed",
    width: 2,
    color: theme.colors.red[6],
  },
  label: {
    position: "insideStartTop",
    color: theme.colors.red[6],
    formatter: (params: any) => params.name,
  },
  data: thresholds.map((threshold) => ({
    yAxis: getThreshold
      ? getThreshold(threshold.metric, threshold.threshold)
      : threshold.threshold,
    name: getThresholdColorAndLabel(threshold.metric).label,
  })),
});
