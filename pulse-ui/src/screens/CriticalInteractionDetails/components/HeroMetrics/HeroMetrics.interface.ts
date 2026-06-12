export interface MetricData {
  title: string;
  value: number;
  trend: string;
  color: string;
  icon: React.ElementType;
}

export interface MetricGroupData {
  title: string;
  description?: string;
  metrics: MetricData[];
  icon?: React.ElementType;
  iconColor?: string;
}

export interface HeroMetricsProps {
  metricGroups?: MetricGroupData[];
}

// Legacy interface - keeping for backward compatibility
export interface InteractionMetricData {
  totalCount: number;
  uniqueUsers: number;
  avgFrequency: number;
  trend: number;
}
