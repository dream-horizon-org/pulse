export interface GeographicLocation {
  name: string;
  value: number;
}

export interface GeographicHeatmapProps {
  data: GeographicLocation[];
  title: string;
  description: string;
  metricLabel: string;
  metricSuffix?: string;
}
