export interface PlatformData {
  platform: string;
  value: number;
}

export interface PlatformDonutChartProps {
  data: PlatformData[];
  title: string;
  description: string;
  metricName: string;
  metricSuffix?: string;
}
