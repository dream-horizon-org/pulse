export type MetricPolarity = "higher_is_better" | "higher_is_worse" | "neutral";

export type MetricValueTone = "good" | "bad" | "neutral";

const EPS = 1e-9;

/** Aligned with backend `RootCauseMetricsRegistry` metric ids. */
const METRIC_POLARITY: Readonly<Record<string, MetricPolarity>> = {
  volume: "neutral",
  apdex: "higher_is_better",
  error_rate: "higher_is_worse",
  poor_user_pct: "higher_is_worse",
  duration_p50: "higher_is_worse",
  duration_p95: "higher_is_worse",
  crash_rate: "higher_is_worse",
  anr_rate: "higher_is_worse",
  frozen_frame_rate: "higher_is_worse",
  slow_frame_rate: "higher_is_worse",
};

export const getMetricPolarity = (metricId: string): MetricPolarity =>
  METRIC_POLARITY[metricId] ?? "neutral";

export const getMetricValueTone = (
  metricId: string,
  valueNumber: number | null | undefined,
  baselineNumber: number | null | undefined,
): MetricValueTone => {
  if (valueNumber == null || baselineNumber == null) return "neutral";
  const polarity = getMetricPolarity(metricId);
  if (polarity === "neutral") return "neutral";
  const diff = valueNumber - baselineNumber;
  if (Math.abs(diff) < EPS) return "neutral";
  const higher = diff > EPS;
  if (polarity === "higher_is_worse") {
    return higher ? "bad" : "good";
  }
  return higher ? "good" : "bad";
};
