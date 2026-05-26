export interface GeographicLocation {
  name: string;
  value: number;
  total?: number;
}

export interface GeographicHeatmapProps {
  data: GeographicLocation[];
  title: string;
  description: string;
  metricLabel: string;
  metricSuffix?: string;
    /** Noun for the `total` field shown on each row, e.g. "interactions", "users". */
    totalUnit?: string;
}
