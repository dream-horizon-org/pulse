export type MetricItem = {
  id: number;
  name: string;
  label: string;
};

export type GetAlertMetricsResponse = {
  scope: string;
  metrics: MetricItem[];
};

export type GetAlertMetricsParams = {
  scope: string;
};

