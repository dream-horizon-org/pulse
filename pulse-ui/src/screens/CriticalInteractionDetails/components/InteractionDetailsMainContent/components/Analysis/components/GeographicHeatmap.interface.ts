export interface GeographicLocation {
  name: string;
  value: number;
  count: number;
}

export interface GeographicHeatmapProps {
  data: GeographicLocation[];
  title: string;
  description: string;
  metricLabel: string;
  metricSuffix?: string;
}
