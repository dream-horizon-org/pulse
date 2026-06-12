import { MantineTheme } from "@mantine/core";

interface SeriesConfig {
  metricType: string;
  name: string;
  color: string;
  dataKey: string;
}

interface Threshold {
  threshold: number;
  metric: string;
}

interface ThresholdLabelConfig {
  label: string;
}

interface CreateSeriesFromConfigProps {
  seriesConfigs: SeriesConfig[];
  metricToShow: string[];
  graphData: any[];
  thresholds?: Threshold[];
  theme?: MantineTheme;
  getThresholdColorAndLabel?: (metric: string) => ThresholdLabelConfig;
  createMarkLineConfig?: (
    thresholds: Threshold[],
    theme: MantineTheme,
    getThresholdColorAndLabel: (metric: string) => ThresholdLabelConfig,
    getThreshold?: (metric: any, threshold: number) => number,
  ) => any;
  getThreshold?: (metric: any, threshold: number) => number;
}

export const createSeriesFromConfig = ({
  seriesConfigs,
  metricToShow,
  graphData,
  thresholds = [],
  theme,
  getThresholdColorAndLabel,
  createMarkLineConfig,
  getThreshold,
}: CreateSeriesFromConfigProps) => {
  const series = [];
  let isFirstSeries = true;

  for (const config of seriesConfigs) {
    if (metricToShow.includes(config.metricType)) {
      series.push({
        name: config.name,
        color: config.color,
        data: graphData.map((d) => d[config.dataKey]),
        smooth: false,
        symbolSize: 0,
        lineStyle: {
          width: 2,
        },
        ...(isFirstSeries &&
          thresholds?.length > 0 &&
          theme &&
          getThresholdColorAndLabel &&
          createMarkLineConfig && {
            markLine: createMarkLineConfig(
              thresholds,
              theme,
              getThresholdColorAndLabel,
              getThreshold,
            ),
          }),
      });
      isFirstSeries = false;
    }
  }

  return series;
};
